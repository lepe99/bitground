package service

import (
	"Bitground-go/config"
	"context"
	"database/sql"
	"fmt"
	"log"
	"time"
)

// UpdateSplit 스플릿: 탈퇴하지 않은 모든 사용자에게 현금을 지급합니다.
// 같은 기준 시각의 지급은 scheduler_runs로 한 번만 실행되므로, 재시도돼도 두 번 지급되지 않는다.
func UpdateSplit(ctx context.Context, db *sql.DB, cfg config.Config, now time.Time) error {
	const kind = "split"
	period := runPeriod(now)

	txCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	err := withTx(txCtx, db, func(tx *sql.Tx) error {
		claimed, err := claimRun(txCtx, tx, kind, period)
		if err != nil {
			return err
		}
		if !claimed {
			log.Printf("스플릿(%s)은 이미 처리되어 건너뜀\n", period)
			return nil
		}

		res, err := tx.ExecContext(txCtx, `UPDATE users SET cash = cash + ? WHERE is_deleted = 0`, seasonStartCash)
		if err != nil {
			return fmt.Errorf("현금 지급 실패: %w", err)
		}
		n, _ := res.RowsAffected()
		log.Printf("스플릿 - 현금 지급: %d명\n", n)
		return nil
	})
	if err != nil {
		return err
	}

	return notifyOnce(ctx, db, kind, period, func() error {
		return NotifySeasonUpdate(ctx, cfg.BackendURL, cfg.SchedulerKey, "split")
	})
}
