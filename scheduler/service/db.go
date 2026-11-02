package service

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"sort"
)

// withTx fn을 한 트랜잭션에서 실행합니다. fn이 에러를 반환하거나 panic이 나면 롤백합니다.
func withTx(ctx context.Context, db *sql.DB, fn func(tx *sql.Tx) error) (err error) {
	tx, err := db.BeginTx(ctx, nil)
	if err != nil {
		return fmt.Errorf("트랜잭션 시작 에러: %w", err)
	}
	defer func() {
		if p := recover(); p != nil {
			_ = tx.Rollback()
			panic(p)
		}
		if err != nil {
			_ = tx.Rollback()
		}
	}()

	if err = fn(tx); err != nil {
		return err
	}
	return tx.Commit()
}

// claimRun 같은 (kind, period) 작업을 한 번만 실행하도록 실행 기록을 남깁니다.
// 정산 작업과 같은 트랜잭션에서 호출해야 기록과 결과가 함께 커밋·롤백된다.
// 이미 기록이 있으면(이전 실행이 커밋됨) false를 반환합니다.
// 동시에 두 실행이 들어오면 뒤의 실행은 앞 트랜잭션이 끝날 때까지 행 락에서 기다린다.
func claimRun(ctx context.Context, tx *sql.Tx, kind, period string) (bool, error) {
	res, err := tx.ExecContext(ctx, `INSERT IGNORE INTO scheduler_runs (kind, period) VALUES (?, ?)`, kind, period)
	if err != nil {
		return false, fmt.Errorf("실행 기록 저장 실패: %w", err)
	}
	n, err := res.RowsAffected()
	if err != nil {
		return false, fmt.Errorf("실행 기록 확인 실패: %w", err)
	}
	return n == 1, nil
}

// notifyOnce 정산이 커밋된 뒤 백엔드에 알립니다. 이미 알린 기록이 있으면 다시 보내지 않습니다.
// 알림이 실패해도 정산은 되돌리지 않고, 다음 재시도에서 알림만 다시 보낸다.
func notifyOnce(ctx context.Context, db *sql.DB, kind, period string, notify func() error) error {
	var notified sql.NullTime
	err := db.QueryRowContext(ctx,
		`SELECT notified_at FROM scheduler_runs WHERE kind = ? AND period = ?`, kind, period).Scan(&notified)
	if err != nil {
		return fmt.Errorf("실행 기록 조회 실패: %w", err)
	}
	if notified.Valid {
		return nil
	}

	if err := notify(); err != nil {
		return fmt.Errorf("정산은 완료됐지만 백엔드 알림 실패 (재시도 시 알림만 다시 보냄): %w", err)
	}

	_, err = db.ExecContext(ctx,
		`UPDATE scheduler_runs SET notified_at = CURRENT_TIMESTAMP(6) WHERE kind = ? AND period = ?`, kind, period)
	if err != nil {
		return fmt.Errorf("알림 완료 기록 실패: %w", err)
	}
	return nil
}

// pricesJSON 코인 ID → 현재가 맵을 SQL JSON_TABLE로 펼칠 수 있는 JSON 배열로 만듭니다.
// 예: [{"id":1,"price":1.5e8}, ...]
// 임시 테이블 없이 집합 연산 한 번에 가격을 조인하기 위해 쓴다.
func pricesJSON(coinPrices map[int]float64) (string, error) {
	type coinPrice struct {
		ID    int     `json:"id"`
		Price float64 `json:"price"`
	}
	list := make([]coinPrice, 0, len(coinPrices))
	for id, price := range coinPrices {
		list = append(list, coinPrice{ID: id, Price: price})
	}
	sort.Slice(list, func(i, j int) bool { return list[i].ID < list[j].ID })

	b, err := json.Marshal(list)
	if err != nil {
		return "", fmt.Errorf("코인 가격 JSON 변환 실패: %w", err)
	}
	return string(b), nil
}

// pricesTable pricesJSON으로 만든 파라미터를 (coin_id, price) 테이블로 펼치는 SQL 조각
const pricesTable = `JSON_TABLE(?, '$[*]' COLUMNS (coin_id INT PATH '$.id', price DOUBLE PATH '$.price'))`
