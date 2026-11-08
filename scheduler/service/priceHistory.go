package service

import (
	"Bitground-go/model"
	"context"
	"database/sql"
	"fmt"
	"time"
)

// UpdateCoinPriceHistory 는 코인 가격 히스토리를 업데이트하는 함수입니다.
func UpdateCoinPriceHistory(ctx context.Context, db *sql.DB, coinPriceHistory map[int]model.UpbitCoinPrice) error {
	// 현재 날짜 및 시간
	now := time.Now()
	currentDate := now.Format("2006-01-02")
	currentHour := now.Hour()

	// 쿼리 타임아웃 설정 (20초)
	queryCtx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()

	return withTx(queryCtx, db, func(tx *sql.Tx) error {
		query := `
		INSERT INTO coin_price_history (coin_id, date, hour, open_price, close_price, high_price, low_price, volume)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE
			open_price = VALUES(open_price),
			close_price = VALUES(close_price),
			high_price = VALUES(high_price),
			low_price = VALUES(low_price),
			volume = VALUES(volume);
	`
		stmt, err := tx.PrepareContext(queryCtx, query)
		if err != nil {
			return fmt.Errorf("쿼리 준비 에러: %w", err)
		}
		defer closeStmt(ctx, stmt)

		// 각 코인 가격 히스토리를 데이터베이스에 삽입
		for coinID, coinPrice := range coinPriceHistory {
			if _, err := stmt.ExecContext(queryCtx,
				coinID, currentDate, currentHour,
				coinPrice.OpenPrice, coinPrice.TradePrice,
				coinPrice.HighPrice, coinPrice.LowPrice,
				coinPrice.TradeVolume); err != nil {
				return fmt.Errorf("코인 가격 히스토리 삽입 에러: %w", err)
			}
		}
		return nil
	})
}
