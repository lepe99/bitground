package service

import (
	"Bitground-go/config"
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"
)

// 시즌 시작·스플릿 때 지급하는 현금
const seasonStartCash = 10000000

// UpdateSeason 시즌 정산: 이전 시즌을 닫고 새 시즌을 연 뒤, 티어 반영·보유 코인 청산·현금 초기화를 합니다.
//
// 각 단계가 앞 단계 결과에 의존하고 같은 테이블(users, orders, user_assets)을 고치므로
// 하나의 트랜잭션에서 순서대로 실행한다. 중간에 실패하면 전부 롤백되어 절반만 정산된 상태가 남지 않는다.
// 같은 종료 시각(period)의 정산은 scheduler_runs로 한 번만 실행된다.
// 새 시즌은 이전 시즌의 종료 시각부터 시작한다 (실행이 늦어져도 일정이 밀리지 않는다).
func UpdateSeason(ctx context.Context, db *sql.DB, cfg config.Config, schedule SeasonSchedule, coinPrices map[int]float64) error {
	const kind = "season"
	period := runPeriod(schedule.EndsAt)

	prices, err := pricesJSON(coinPrices)
	if err != nil {
		return err
	}

	txCtx, cancel := context.WithTimeout(ctx, 60*time.Second)
	defer cancel()

	err = withTx(txCtx, db, func(tx *sql.Tx) error {
		claimed, err := claimRun(txCtx, tx, kind, period)
		if err != nil {
			return err
		}
		if !claimed {
			slog.InfoContext(ctx, "시즌 정산은 이미 처리되어 건너뜀", "run_period", period)
			return nil
		}
		return settleSeason(txCtx, tx, cfg, schedule.EndsAt, schedule.ID, prices)
	})
	if err != nil {
		return err
	}

	return notifyOnce(ctx, db, kind, period, func() error {
		return NotifySeasonUpdate(ctx, cfg.BackendURL, cfg.SchedulerKey, "season")
	})
}

func settleSeason(ctx context.Context, tx *sql.Tx, cfg config.Config, base time.Time, seasonID int, prices string) error {
	// 1. 이전 시즌 확인 (정산 중 다른 실행이 같은 시즌을 건드리지 못하도록 행 락)
	var ogSeasonName, status string
	err := tx.QueryRowContext(ctx, `SELECT name, status FROM seasons WHERE id = ? FOR UPDATE`, seasonID).
		Scan(&ogSeasonName, &status)
	if errors.Is(err, sql.ErrNoRows) {
		return fmt.Errorf("시즌 %d을 찾을 수 없음", seasonID)
	} else if err != nil {
		return fmt.Errorf("시즌 조회 실패: %w", err)
	}
	if status != "PENDING" {
		return fmt.Errorf("시즌 %d은 이미 종료됨 (status=%s)", seasonID, status)
	}

	startAt, endAt, splitAt, endsAt := nextSeasonSchedule(base)
	newSeasonName := nextSeasonName(base, ogSeasonName)

	steps := []struct {
		name  string
		query string
		args  []any
	}{
		// 2. 이전 시즌 종료 (보상 계산 완료 표시 포함)
		{"이전 시즌 종료", `UPDATE seasons SET status = 'COMPLETED', reward_calculated = 1 WHERE id = ?`,
			[]any{seasonID}},
		// 3. 새 시즌 시작
		{"새 시즌 생성", `INSERT INTO seasons (name, start_at, end_at, split_starts_at, ends_at) VALUES (?, ?, ?, ?, ?)`,
			[]any{newSeasonName, startAt.Format(time.DateOnly), endAt.Format(time.DateOnly),
				splitAt.Format(time.DateTime), endsAt.Format(time.DateTime)}},
		// 4. 이전 시즌 최종 랭킹의 티어를 사용자에게 반영 (랭킹에 없으면 0)
		{"티어 반영", `
			UPDATE users u
			LEFT JOIN user_rankings ur ON u.id = ur.user_id AND ur.season_id = ?
			SET u.tier = IFNULL(ur.tier, 0)
			WHERE u.is_deleted = 0`,
			[]any{seasonID}},
		// 5. 보유 코인을 현재가로 청산한 기록을 이전 시즌 체결 내역에 남김
		//    (가격 정보가 없는 코인(상장 폐지 등)은 기록 없이 아래 6단계에서 삭제됨)
		{"보유 코인 청산 기록", `
			INSERT INTO orders (amount, symbol_id, user_id, season_id, trade_price, order_type, status)
			SELECT ua.amount, ua.symbol_id, ua.user_id, ?, p.price, 'SELL', 'COMPLETED'
			FROM user_assets ua
			JOIN ` + pricesTable + ` p ON p.coin_id = ua.symbol_id`,
			[]any{seasonID, prices}},
		// 6. 보유 자산 초기화
		{"보유 자산 초기화", `DELETE FROM user_assets`, nil},
		// 7. 이전 시즌 미체결 예약 주문 삭제 (Redis 대기열은 백엔드가 알림을 받고 다시 맞춘다)
		{"미체결 예약 주문 삭제", `DELETE FROM orders WHERE season_id = ? AND status = 'PENDING'`,
			[]any{seasonID}},
		// 8. 현금 초기화
		{"현금 초기화", `UPDATE users SET cash = ? WHERE is_deleted = 0`,
			[]any{seasonStartCash}},
	}

	for _, st := range steps {
		res, err := tx.ExecContext(ctx, st.query, st.args...)
		if err != nil {
			return fmt.Errorf("%s 실패: %w", st.name, err)
		}
		n, _ := res.RowsAffected()
		slog.InfoContext(ctx, "시즌 정산 단계", "step", st.name, "rows", n)
	}

	slog.InfoContext(ctx, "시즌 정산 완료", "from_season", ogSeasonName, "to_season", newSeasonName,
		"split_starts_at", splitAt.Format(time.DateTime), "ends_at", endsAt.Format(time.DateTime))
	return nil
}

// runPeriod 정산 작업의 중복 실행을 막는 기간 키 (기준 시각, 시간 단위)
func runPeriod(now time.Time) string {
	return now.Format("2006-01-02T15")
}

// seasonNamePattern "26년 3시즌" 형식의 이름
var seasonNamePattern = regexp.MustCompile(`^(\d{2})년 (\d+)시즌$`)

// nextSeasonName 새 시즌 이름: "{시작 연도 두 자리}년 {그해 몇 번째}시즌" (예: 26년 1시즌, 26년 2시즌, 27년 1시즌).
// 이전 시즌이 같은 해의 이 형식이면 번호 +1, 해가 바뀌었거나 형식이 다르면(예전 이름) 1부터
func nextSeasonName(start time.Time, prevName string) string {
	year := start.Year() % 100
	n := 1
	if m := seasonNamePattern.FindStringSubmatch(prevName); m != nil {
		prevYear, _ := strconv.Atoi(m[1])
		prevN, _ := strconv.Atoi(m[2])
		if prevYear == year {
			n = prevN + 1
		}
	}
	return fmt.Sprintf("%02d년 %d시즌", year, n)
}

// NotifySeasonUpdate 백엔드에 시즌/스플릿 완료를 알립니다. (예약 주문 정리, 사용자 알림)
// 공유 키는 본문이 아니라 헤더로 보낸다.
func NotifySeasonUpdate(ctx context.Context, backendURL, schedulerKey, seasonFlag string) error {
	reqCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	form := url.Values{"seasonFlag": {seasonFlag}}
	req, err := http.NewRequestWithContext(reqCtx, http.MethodPost, backendURL+"/seasons/update", strings.NewReader(form.Encode()))
	if err != nil {
		return fmt.Errorf("HTTP 요청 생성 에러: %w", err)
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	req.Header.Set("X-Scheduler-Key", schedulerKey)

	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("API 요청 에러: %w", err)
	}
	defer resp.Body.Close()

	// 백엔드는 처리 완료 시 204 No Content를 반환하므로 2xx 전체를 성공으로 본다
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return fmt.Errorf("서버 응답 에러: 상태 코드 %d", resp.StatusCode)
	}

	return nil
}
