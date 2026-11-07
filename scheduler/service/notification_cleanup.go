package service

import (
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"time"
)

// NotificationRetentionDays 알림 보관 기간. 이보다 오래된 알림은 매일 0시에 지운다
const NotificationRetentionDays = 90

// 한 번에 지우는 행 수 (큰 DELETE 하나로 테이블을 오래 잠그지 않도록 나눠 지운다)
const notificationPurgeBatch = 5000

// PurgeOldNotifications 보관 기간이 지난 알림을 지우고 지운 개수를 반환한다
func PurgeOldNotifications(ctx context.Context, db *sql.DB, now time.Time) (int64, error) {
	cutoff := now.AddDate(0, 0, -NotificationRetentionDays)
	var total int64
	for {
		res, err := db.ExecContext(ctx,
			`DELETE FROM notifications WHERE created_at < ? ORDER BY created_at LIMIT ?`,
			cutoff, notificationPurgeBatch)
		if err != nil {
			return total, fmt.Errorf("오래된 알림 삭제 실패: %w", err)
		}
		n, err := res.RowsAffected()
		if err != nil {
			return total, fmt.Errorf("삭제한 알림 수 확인 실패: %w", err)
		}
		total += n
		if n < notificationPurgeBatch {
			break
		}
	}
	slog.InfoContext(ctx, "오래된 알림 삭제", "deleted", total, "before", cutoff.Format(time.DateTime))
	return total, nil
}
