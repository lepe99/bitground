package service

import (
	"Bitground-go/config"
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log"
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
// 같은 기준 시각(period)의 정산은 scheduler_runs로 한 번만 실행된다.
func UpdateSeason(ctx context.Context, db *sql.DB, cfg config.Config, now time.Time, seasonID int, coinPrices map[int]float64) error {
	const kind = "season"
	period := runPeriod(now)

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
			log.Printf("시즌 정산(%s)은 이미 처리되어 건너뜀\n", period)
			return nil
		}
		return settleSeason(txCtx, tx, cfg, now, seasonID, prices)
	})
	if err != nil {
		return err
	}

	return notifyOnce(ctx, db, kind, period, func() error {
		return NotifySeasonUpdate(ctx, cfg.BackendURL, cfg.SchedulerKey, "season")
	})
}

func settleSeason(ctx context.Context, tx *sql.Tx, cfg config.Config, now time.Time, seasonID int, prices string) error {
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

	newSeasonName, err := processSeasonStrings(cfg.SeasonName, ogSeasonName)
	if err != nil {
		return fmt.Errorf("시즌 이름 처리 실패: %w", err)
	}
	startAt, endAt, err := seasonPeriod(now, cfg.Mode)
	if err != nil {
		return err
	}
	splitAt, endsAt := seasonMilestones(now, cfg.Mode, endAt)

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
		log.Printf("시즌 정산 - %s: %d행\n", st.name, n)
	}

	log.Printf("시즌 정산 완료: %s → %s (%s ~ %s)\n", ogSeasonName, newSeasonName,
		startAt.Format(time.DateOnly), endAt.Format(time.DateOnly))
	return nil
}

// runPeriod 정산 작업의 중복 실행을 막는 기간 키 (기준 시각, 시간 단위)
func runPeriod(now time.Time) string {
	return now.Format("2006-01-02T15")
}

// seasonPeriod 새 시즌의 시작일과 종료일을 계산합니다.
//   - prod: 1일 시작 → 15일 종료, 16일 시작 → 말일 종료
//   - dev: 6시간 주기라 시작일과 종료일이 같다
func seasonPeriod(now time.Time, mode string) (time.Time, time.Time, error) {
	if mode == "dev" {
		return now, now, nil
	}
	switch now.Day() {
	case 1:
		return now, now.AddDate(0, 0, 14), nil
	case 16:
		firstOfNextMonth := time.Date(now.Year(), now.Month()+1, 1, 0, 0, 0, 0, now.Location())
		return now, firstOfNextMonth.AddDate(0, 0, -1), nil
	default:
		return time.Time{}, time.Time{}, fmt.Errorf("시즌 종료일 설정 실패: %d일은 시즌 시작일이 아님", now.Day())
	}
}

// seasonMilestones 새 시즌의 스플릿 2 시작 시각과 시즌 종료(다음 시즌 시작) 시각. 화면이 남은 시간을 보여줄 때 쓴다.
// TimeCheck와 같은 일정이어야 한다: prod는 시작 7일 뒤 0시(8일·23일)에 스플릿, 종료일 다음 날 0시에 정산.
// dev는 6시간 시즌(0·6·12·18시)의 3시간째에 스플릿. 문자열로 넣어 DB 연결 시간대와 상관없이 한국 시각 그대로 저장된다
func seasonMilestones(now time.Time, mode string, endAt time.Time) (time.Time, time.Time) {
	if mode == "dev" {
		return now.Add(3 * time.Hour), now.Add(6 * time.Hour)
	}
	day := time.Date(now.Year(), now.Month(), now.Day(), 0, 0, 0, 0, now.Location())
	end := time.Date(endAt.Year(), endAt.Month(), endAt.Day(), 0, 0, 0, 0, endAt.Location())
	return day.AddDate(0, 0, 7), end.AddDate(0, 0, 1)
}

// processSeasonStrings 함수는 문자열 a와 b를 비교하여 새로운 문자열 c를 반환합니다.
func processSeasonStrings(seasonName, ogSeasonName string) (string, error) {
	// "시즌" 앞의 문자열과 "시즌" 자체를 캡쳐하는 정규 표현식 (예: "### 시즌", "시즌")
	reSeasonPrefix := regexp.MustCompile(`^(.*시즌)`)
	// "시즌 " 뒤의 숫자를 캡쳐하는 정규 표현식
	reNumber := regexp.MustCompile(`시즌 (\d+)$`)

	// 문자열 a에서 "시즌" 부분을 포함한 앞부분을 추출합니다.
	matchesNewPrefix := reSeasonPrefix.FindStringSubmatch(seasonName)
	var seasonPrefixNew string
	if len(matchesNewPrefix) > 1 {
		seasonPrefixNew = matchesNewPrefix[1] // 예: "### 시즌" 또는 "시즌"
	} else {
		return "", fmt.Errorf("문자열 '%s'에서 '시즌' 부분을 찾을 수 없습니다", seasonName)
	}

	// 문자열 b에서 "시즌" 부분을 포함한 앞부분을 추출합니다.
	matchesOgPrefix := reSeasonPrefix.FindStringSubmatch(ogSeasonName)
	var seasonPrefixOg string
	if len(matchesOgPrefix) > 1 {
		seasonPrefixOg = matchesOgPrefix[1] // 예: "~~~ 시즌" 또는 "시즌"
	} else {
		return "", fmt.Errorf("문자열 '%s'에서 '시즌' 부분을 찾을 수 없습니다", ogSeasonName)
	}

	// 문자열 b에서 숫자를 추출합니다.
	matchesOgNumber := reNumber.FindStringSubmatch(ogSeasonName)
	var numberOg int
	if len(matchesOgNumber) > 1 {
		num, err := strconv.Atoi(matchesOgNumber[1])
		if err != nil {
			return "", fmt.Errorf("문자열 '%s'에서 숫자를 파싱할 수 없습니다: %w", ogSeasonName, err)
		}
		numberOg = num
	} else {
		// 숫자가 없는 경우 (예: "시즌"만 있는 경우) 기본값을 0으로 설정하여 로직에서 1이 되도록 함
		numberOg = 0
	}

	// 문자열 a의 "### 시즌" 부분과 문자열 b의 "~~~ 시즌" 부분을 비교합니다.
	// 즉, '시즌'을 포함한 앞부분이 완전히 같은지 비교합니다.
	if seasonPrefixNew == seasonPrefixOg {
		// 같다면 ** 값을 1 증가시킵니다.
		numberOg++
	} else {
		// 다르다면 ** 값을 1로 만듭니다.
		numberOg = 1
	}

	// 문자열 c를 "### 시즌 **" 양식으로 출력합니다.
	// 이때, 문자열 c의 "### 시즌" 부분은 문자열 a에서 추출한 seasonPrefixA를 사용합니다.
	c := fmt.Sprintf("%s %d", seasonPrefixNew, numberOg)
	return c, nil
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
