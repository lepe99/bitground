package service

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"time"
)

// 시즌 일정은 DB(seasons.split_starts_at, ends_at)가 기준이다.
// 스케줄러는 매시 정각에 진행 중인 시즌의 일정을 읽어, 시각이 지났으면 스플릿·정산을 한다.
// 관리자가 백엔드에서 일정을 바꾸면 다음 정각 실행에 바로 반영된다 (Lambda 일정은 그대로 매시 정각).
// 일정은 정각 단위로만 바꿀 수 있다 (백엔드가 검사).

// SeasonSchedule 진행 중인 시즌과 일정
type SeasonSchedule struct {
	ID      int
	SplitAt time.Time // 스플릿 2 시작 (추가 자금 지급). 없으면 zero
	EndsAt  time.Time // 시즌 정산(종료) 시각. 없으면 zero
	// SplitDone 이 시즌의 스플릿을 이미 처리함 (같은 시각의 스플릿 실행 기록이 있음)
	SplitDone bool
}

// SeasonDue 지금 시즌 정산을 해야 하면 true (종료 시각이 지남. 늦게 실행돼도 따라잡는다)
func (s SeasonSchedule) SeasonDue(now time.Time) bool {
	return !s.EndsAt.IsZero() && !now.Before(s.EndsAt)
}

// SplitDue 지금 스플릿을 해야 하면 true. 같은 실행에서 시즌 정산도 하면 현금이 어차피 초기화되므로 하지 않는다
func (s SeasonSchedule) SplitDue(now time.Time) bool {
	return !s.SplitAt.IsZero() && !now.Before(s.SplitAt) && !s.SplitDone && !s.SeasonDue(now)
}

// GetSeasonSchedule 진행 중인 시즌의 일정을 읽습니다. DB의 DATETIME은 한국 시각 문자열로 읽어 loc 기준으로 해석한다
// (드라이버의 시간대 설정과 상관없이 저장한 그대로)
func GetSeasonSchedule(ctx context.Context, db *sql.DB, loc *time.Location) (SeasonSchedule, error) {
	queryCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	var s SeasonSchedule
	var splitAt, endsAt sql.NullString
	err := db.QueryRowContext(queryCtx, `
		SELECT id, DATE_FORMAT(split_starts_at, '%Y-%m-%d %H:%i:%s'), DATE_FORMAT(ends_at, '%Y-%m-%d %H:%i:%s')
		FROM seasons WHERE status = 'PENDING' ORDER BY start_at DESC, id DESC LIMIT 1`).
		Scan(&s.ID, &splitAt, &endsAt)
	if err != nil {
		return SeasonSchedule{}, fmt.Errorf("진행 중인 시즌 조회 실패: %w", err)
	}
	if s.SplitAt, err = parseDBTime(splitAt, loc); err != nil {
		return SeasonSchedule{}, err
	}
	if s.EndsAt, err = parseDBTime(endsAt, loc); err != nil {
		return SeasonSchedule{}, err
	}
	if s.EndsAt.IsZero() {
		slog.WarnContext(ctx, "시즌 종료 시각이 비어 있어 정산하지 않음", "season_id", s.ID)
	}

	if !s.SplitAt.IsZero() {
		var n int
		if err := db.QueryRowContext(queryCtx, `SELECT COUNT(*) FROM scheduler_runs WHERE kind = 'split' AND period = ?`,
			runPeriod(s.SplitAt)).Scan(&n); err != nil {
			return SeasonSchedule{}, fmt.Errorf("스플릿 실행 기록 조회 실패: %w", err)
		}
		s.SplitDone = n > 0
	}
	return s, nil
}

func parseDBTime(v sql.NullString, loc *time.Location) (time.Time, error) {
	if !v.Valid || v.String == "" {
		return time.Time{}, nil
	}
	t, err := time.ParseInLocation(time.DateTime, v.String, loc)
	if err != nil {
		return time.Time{}, fmt.Errorf("시즌 일정 시각 해석 실패 (%q): %w", v.String, err)
	}
	return t, nil
}

// nextSeasonSchedule 정산 시각(base, 이전 시즌 종료 = 새 시즌 시작)으로 새 시즌의 기본 일정을 정한다.
//   - prod: 1일·16일 0시 경계 중 base에서 7일 이상 남은 첫 경계에 종료, 시작 7일 뒤 0시(기간이 짧으면 절반)에 스플릿.
//     평소 일정(1일·16일 시작)이면 8일·23일 스플릿, 16일·다음 달 1일 종료. 관리자가 종료를 옮겨도 다음 시즌부터 경계에 다시 맞춘다
//   - dev: 3시간 뒤 스플릿, 6시간 뒤 종료
//
// startAt·endAt은 화면·기록용 날짜(시작일, 종료 전날)
func nextSeasonSchedule(base time.Time, mode string) (startAt, endAt, splitAt, endsAt time.Time) {
	if mode == "dev" {
		return base, base, base.Add(3 * time.Hour), base.Add(6 * time.Hour)
	}
	day := time.Date(base.Year(), base.Month(), base.Day(), 0, 0, 0, 0, base.Location())
	endsAt = nextBoundary(base.AddDate(0, 0, 7))
	days := int(endsAt.Sub(day).Hours() / 24)
	splitAt = day.AddDate(0, 0, min(7, days/2))
	if !splitAt.After(base) {
		splitAt = day.AddDate(0, 0, 1)
	}
	return day, endsAt.AddDate(0, 0, -1), splitAt, endsAt
}

// nextBoundary t 이후(같으면 t)의 첫 시즌 경계 (매월 1일·16일 0시)
func nextBoundary(t time.Time) time.Time {
	for _, b := range []time.Time{
		time.Date(t.Year(), t.Month(), 1, 0, 0, 0, 0, t.Location()),
		time.Date(t.Year(), t.Month(), 16, 0, 0, 0, 0, t.Location()),
		time.Date(t.Year(), t.Month()+1, 1, 0, 0, 0, 0, t.Location()),
	} {
		if !b.Before(t) {
			return b
		}
	}
	panic("unreachable")
}

// RetryPendingNotifications 정산·스플릿은 끝났지만 백엔드 알림이 실패한 기록을 다시 알린다 (최근 하루).
// 정산 뒤에는 진행 중인 시즌이 바뀌어 같은 기록을 다시 처리할 일이 없으므로 따로 챙긴다
func RetryPendingNotifications(ctx context.Context, db *sql.DB, backendURL, schedulerKey string) error {
	rows, err := db.QueryContext(ctx, `
		SELECT kind, period FROM scheduler_runs
		WHERE notified_at IS NULL AND kind IN ('season', 'split') AND created_at >= NOW() - INTERVAL 1 DAY`)
	if err != nil {
		return fmt.Errorf("알림 대기 기록 조회 실패: %w", err)
	}
	type run struct{ kind, period string }
	var pending []run
	for rows.Next() {
		var r run
		if err := rows.Scan(&r.kind, &r.period); err != nil {
			rows.Close()
			return err
		}
		pending = append(pending, r)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return err
	}

	var firstErr error
	for _, r := range pending {
		slog.InfoContext(ctx, "보내지 못한 백엔드 알림 재시도", "kind", r.kind, "run_period", r.period)
		if err := notifyOnce(ctx, db, r.kind, r.period, func() error {
			return NotifySeasonUpdate(ctx, backendURL, schedulerKey, r.kind)
		}); err != nil && firstErr == nil {
			firstErr = err
		}
	}
	return firstErr
}
