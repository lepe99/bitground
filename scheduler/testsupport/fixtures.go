package testsupport

import (
	"context"
	"database/sql"
	"testing"
	"time"
)

// Fixtures 테스트 데이터를 넣고 결과를 읽는 짧은 SQL 도우미
type Fixtures struct {
	T   *testing.T
	DB  *sql.DB
	Ctx context.Context
}

func (f Fixtures) insert(query string, args ...any) int {
	f.T.Helper()
	res, err := f.DB.ExecContext(f.Ctx, query, args...)
	if err != nil {
		f.T.Fatalf("테스트 데이터 넣기 실패: %v\n%s", err, query)
	}
	id, _ := res.LastInsertId()
	return int(id)
}

// Coin 코인 (상장 상태)
func (f Fixtures) Coin(symbol, koreanName string) int {
	return f.insert(`INSERT INTO coins (symbol, korean_name, trade_price_24h, change_rate) VALUES (?, ?, 0, 0)`,
		symbol, koreanName)
}

// Season 진행 중인 시즌. splitAt·endsAt은 KST 시각 (zero면 NULL)
func (f Fixtures) Season(name string, start time.Time, splitAt, endsAt time.Time) int {
	return f.insert(`INSERT INTO seasons (name, start_at, end_at, split_starts_at, ends_at, status) VALUES (?, ?, ?, ?, ?, 'PENDING')`,
		name, start.Format(time.DateOnly), endsAt.AddDate(0, 0, -1).Format(time.DateOnly), dbTime(splitAt), dbTime(endsAt))
}

func dbTime(t time.Time) any {
	if t.IsZero() {
		return nil
	}
	return t.In(KST).Format(time.DateTime)
}

// User 사용자 (cash 원)
func (f Fixtures) User(cash int64) int {
	return f.insert(`INSERT INTO users (cash, name, provider, provider_id) VALUES (?, 'user', 'google', UUID())`, cash)
}

// DeletedUser 탈퇴한 사용자
func (f Fixtures) DeletedUser(cash int64) int {
	return f.insert(`INSERT INTO users (cash, name, provider, provider_id, is_deleted) VALUES (?, 'gone', 'google', UUID(), 1)`, cash)
}

// Trade 체결된 주문 (랭킹 참가 조건)
func (f Fixtures) Trade(userID, coinID, seasonID int, orderType string, amount, price float64) int {
	return f.insert(`INSERT INTO orders (amount, symbol_id, user_id, season_id, trade_price, order_type, status)
		VALUES (?, ?, ?, ?, ?, ?, 'COMPLETED')`, amount, coinID, userID, seasonID, price, orderType)
}

// Reserve 미체결 예약 주문
func (f Fixtures) Reserve(userID, coinID, seasonID int, orderType string, amount, reservePrice float64) int {
	return f.insert(`INSERT INTO orders (amount, symbol_id, user_id, season_id, reserve_price, order_type, status)
		VALUES (?, ?, ?, ?, ?, ?, 'PENDING')`, amount, coinID, userID, seasonID, reservePrice, orderType)
}

// Asset 보유 자산
func (f Fixtures) Asset(userID, coinID int, amount, avgPrice float64) int {
	return f.insert(`INSERT INTO user_assets (user_id, symbol_id, amount, avg_price) VALUES (?, ?, ?, ?)`,
		userID, coinID, amount, avgPrice)
}

// PriceHistory 시간별 시세 기록 (상장 폐지 정리 가격)
func (f Fixtures) PriceHistory(coinID int, at time.Time, closePrice float64) {
	f.insert(`INSERT INTO coin_price_history (coin_id, date, hour, open_price, close_price, high_price, low_price, volume)
		VALUES (?, ?, ?, ?, ?, ?, ?, 0)`, coinID, at.Format(time.DateOnly), at.Hour(), closePrice, closePrice, closePrice, closePrice)
}

// Notification createdAt에 만든 알림
func (f Fixtures) Notification(userID int, createdAt time.Time) int {
	return f.insert(`INSERT INTO notifications (user_id, message, message_type, is_read, created_at) VALUES (?, 'old', 'NOTICE', 0, ?)`,
		userID, createdAt.In(KST).Format(time.DateTime))
}

// Int 숫자 하나를 읽는다
func (f Fixtures) Int(query string, args ...any) int64 {
	f.T.Helper()
	var v sql.NullInt64
	if err := f.DB.QueryRowContext(f.Ctx, query, args...).Scan(&v); err != nil {
		f.T.Fatalf("조회 실패: %v\n%s", err, query)
	}
	return v.Int64
}

// String 문자열 하나를 읽는다 (NULL이면 "")
func (f Fixtures) String(query string, args ...any) string {
	f.T.Helper()
	var v sql.NullString
	if err := f.DB.QueryRowContext(f.Ctx, query, args...).Scan(&v); err != nil {
		f.T.Fatalf("조회 실패: %v\n%s", err, query)
	}
	return v.String
}

// Rows 여러 행을 문자열로 읽는다 (각 행은 열 값을 순서대로)
func (f Fixtures) Rows(query string, args ...any) [][]string {
	f.T.Helper()
	rows, err := f.DB.QueryContext(f.Ctx, query, args...)
	if err != nil {
		f.T.Fatalf("조회 실패: %v\n%s", err, query)
	}
	defer rows.Close()
	cols, _ := rows.Columns()
	var out [][]string
	for rows.Next() {
		vals := make([]sql.NullString, len(cols))
		ptrs := make([]any, len(cols))
		for i := range vals {
			ptrs[i] = &vals[i]
		}
		if err := rows.Scan(ptrs...); err != nil {
			f.T.Fatal(err)
		}
		row := make([]string, len(cols))
		for i, v := range vals {
			row[i] = v.String
		}
		out = append(out, row)
	}
	return out
}
