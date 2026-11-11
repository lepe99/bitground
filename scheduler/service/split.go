package service

import (
	"Bitground-go/config"
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"time"
)

// UpdateSplit 스플릿: 탈퇴하지 않은 모든 사용자에게 현금을 지급합니다.
// 같은 기준 시각의 지급은 scheduler_runs로 한 번만 실행되므로, 재시도돼도 두 번 지급되지 않는다.
func UpdateSplit(ctx context.Context, db *sql.DB, cfg config.Config, splitAt time.Time) error {
	const kind = "split"
	// 시즌의 스플릿 시각을 키로 써, 늦게 실행되거나 재시도해도 한 시즌에 한 번만 지급한다
	period := runPeriod(splitAt)

	txCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	err := withTx(txCtx, db, func(tx *sql.Tx) error {
		claimed, err := claimRun(txCtx, tx, kind, period)
		if err != nil {
			return err
		}
		if !claimed {
			slog.InfoContext(ctx, "스플릿은 이미 처리되어 건너뜀", "run_period", period)
			return nil
		}

		res, err := tx.ExecContext(txCtx, `UPDATE users SET cash = cash + ? WHERE is_deleted = 0`, splitCash)
		if err != nil {
			return fmt.Errorf("현금 지급 실패: %w", err)
		}
		n, _ := res.RowsAffected()
		slog.InfoContext(ctx, "스플릿 현금 지급", "users", n)
		return nil
	})
	if err != nil {
		return err
	}

	return notifyOnce(ctx, db, kind, period, func() error {
		return NotifySeasonUpdate(ctx, cfg.BackendURL, cfg.SchedulerKey, "split")
	})
}
