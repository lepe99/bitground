package service

import (
	"Bitground-go/util"
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log/slog"
	"math/big"
	"strconv"
	"strings"
	"time"
)

// 한 번에 상장 폐지로 처리할 수 있는 최대 비율. 업비트 응답이 비정상적으로 짧게 오면
// 멀쩡한 코인을 한꺼번에 정리해 버리지 않도록, 이보다 많이 사라지면 아무것도 바꾸지 않는다
const maxDelistRatio = 0.1

// SyncDelistings 업비트 KRW 시세 목록과 DB 코인 목록을 맞춘다 (매시간).
//
// 목록에서 사라진 코인(거래지원 종료)은 상장 폐지로 표시하고, 실제 거래소처럼 매수·매도를 막는다(백엔드).
// 게임에는 출금이 없으므로 보유분은 마지막 시세로 팔아 현금으로 돌려주고, 그 코인의 예약 주문은 취소한 뒤 알린다.
// 목록에 다시 나타난 코인은 표시를 푼다.
func SyncDelistings(ctx context.Context, db *sql.DB) error {
	details, err := getCoinDetails(ctx)
	if err != nil {
		return fmt.Errorf("업비트 시세 목록 조회 실패: %w", err)
	}
	listed := make(map[string]bool, len(details))
	for symbol := range details {
		listed[symbol] = true
	}

	txCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	return withTx(txCtx, db, func(tx *sql.Tx) error {
		return syncDelistings(txCtx, tx, listed)
	})
}

type coinRow struct {
	id         int
	symbol     string
	koreanName string
	deleted    bool
}

func syncDelistings(ctx context.Context, tx *sql.Tx, listed map[string]bool) error {
	rows, err := tx.QueryContext(ctx, `SELECT id, symbol, korean_name, is_deleted FROM coins FOR UPDATE`)
	if err != nil {
		return fmt.Errorf("코인 목록 조회 실패: %w", err)
	}
	var coins []coinRow
	for rows.Next() {
		var c coinRow
		if err := rows.Scan(&c.id, &c.symbol, &c.koreanName, &c.deleted); err != nil {
			rows.Close()
			return fmt.Errorf("코인 목록 읽기 실패: %w", err)
		}
		coins = append(coins, c)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return fmt.Errorf("코인 목록 읽기 실패: %w", err)
	}

	delisted, relisted, err := planDelistings(coins, listed)
	if err != nil {
		return err
	}
	if len(relisted) > 0 {
		if err := setDeleted(ctx, tx, relisted, false); err != nil {
			return err
		}
		slog.InfoContext(ctx, "다시 상장된 코인 표시 해제", "coins", symbolsOf(relisted))
	}
	if len(delisted) == 0 {
		return nil
	}
	if err := setDeleted(ctx, tx, delisted, true); err != nil {
		return err
	}
	slog.WarnContext(ctx, "상장 폐지 코인 처리", "coins", symbolsOf(delisted))
	return liquidateDelisted(ctx, tx, delisted)
}

// planDelistings 상장 폐지로 바꿀 코인과 다시 상장된 코인을 고른다.
// 목록이 비었거나 너무 많이 사라졌으면(업비트 응답 이상) 에러로 멈춘다
func planDelistings(coins []coinRow, listed map[string]bool) (delisted, relisted []coinRow, err error) {
	active := 0
	for _, c := range coins {
		switch {
		case !c.deleted && !listed[c.symbol]:
			delisted = append(delisted, c)
			active++
		case !c.deleted:
			active++
		case c.deleted && listed[c.symbol]:
			relisted = append(relisted, c)
		}
	}
	if len(listed) == 0 {
		return nil, nil, errors.New("업비트 시세 목록이 비어 있어 상장 폐지 확인을 건너뜀")
	}
	if limit := max(1, int(float64(active)*maxDelistRatio)); len(delisted) > limit {
		return nil, nil, fmt.Errorf("사라진 코인이 %d개로 너무 많아(상한 %d) 업비트 응답 이상으로 보고 건너뜀: %v",
			len(delisted), limit, symbolsOf(delisted))
	}
	return delisted, relisted, nil
}

func setDeleted(ctx context.Context, tx *sql.Tx, coins []coinRow, deleted bool) error {
	query := fmt.Sprintf(`UPDATE coins SET is_deleted = ? WHERE id IN (%s)`, util.GeneratePlaceholders(len(coins)))
	if _, err := tx.ExecContext(ctx, query, append([]any{deleted}, idsOf(coins)...)...); err != nil {
		return fmt.Errorf("상장 폐지 표시 변경 실패: %w", err)
	}
	return nil
}

// liquidateDelisted 상장 폐지된 코인의 보유분을 마지막 시세로 팔고, 예약 주문을 취소하고, 사용자에게 알린다
func liquidateDelisted(ctx context.Context, tx *sql.Tx, coins []coinRow) error {
	var seasonID int
	err := tx.QueryRowContext(ctx, `SELECT id FROM seasons WHERE status = 'PENDING' ORDER BY id DESC LIMIT 1`).Scan(&seasonID)
	if err != nil {
		return fmt.Errorf("진행 중인 시즌 조회 실패: %w", err)
	}
	names := make(map[int]string, len(coins))
	for _, c := range coins {
		names[c.id] = c.koreanName
	}
	ids := idsOf(coins)
	in := util.GeneratePlaceholders(len(coins))

	// 1. 보유분: 마지막 시간별 종가로 판 기록을 남기고, 판 금액(원 단위 내림)을 현금에 더한다
	rows, err := tx.QueryContext(ctx, fmt.Sprintf(`
		SELECT ua.id, ua.user_id, ua.symbol_id, CAST(ua.amount AS CHAR), CAST(p.price AS CHAR)
		FROM user_assets ua
		JOIN (
		    -- 시세 기록은 FLOAT라 0.0123이 0.01229999…로 읽히므로 소수 8자리(DB 가격 자릿수)로 반올림
		    SELECT coin_id, CAST(ROUND(close_price, 8) AS DECIMAL(24, 8)) AS price
		    FROM (SELECT coin_id, close_price,
		                 ROW_NUMBER() OVER (PARTITION BY coin_id ORDER BY date DESC, hour DESC) AS rn
		          FROM coin_price_history WHERE coin_id IN (%s)) h
		    WHERE rn = 1
		) p ON p.coin_id = ua.symbol_id
		WHERE ua.symbol_id IN (%s)`, in, in), append(ids, ids...)...)
	if err != nil {
		return fmt.Errorf("정리할 보유 자산 조회 실패: %w", err)
	}
	type holding struct {
		assetID, userID, coinID int
		amount, price           string
	}
	var holdings []holding
	for rows.Next() {
		var h holding
		if err := rows.Scan(&h.assetID, &h.userID, &h.coinID, &h.amount, &h.price); err != nil {
			rows.Close()
			return fmt.Errorf("보유 자산 읽기 실패: %w", err)
		}
		holdings = append(holdings, h)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return fmt.Errorf("보유 자산 읽기 실패: %w", err)
	}

	for _, h := range holdings {
		krw, err := sellValue(h.amount, h.price)
		if err != nil {
			return err
		}
		steps := []struct {
			name  string
			query string
			args  []any
		}{
			{"정리 매도 기록", `INSERT INTO orders (amount, symbol_id, user_id, season_id, trade_price, order_type, status)
				VALUES (?, ?, ?, ?, ?, 'SELL', 'COMPLETED')`, []any{h.amount, h.coinID, h.userID, seasonID, h.price}},
			{"정리 대금 입금", `UPDATE users SET cash = cash + ? WHERE id = ?`, []any{krw, h.userID}},
			{"보유 자산 삭제", `DELETE FROM user_assets WHERE id = ?`, []any{h.assetID}},
			{"정리 알림", `INSERT INTO notifications (user_id, message, message_type, is_read) VALUES (?, ?, 'ORDER_EXECUTION', 0)`,
				[]any{h.userID, liquidationMessage(names[h.coinID], h.amount, h.price, krw)}},
		}
		for _, st := range steps {
			if _, err := tx.ExecContext(ctx, st.query, st.args...); err != nil {
				return fmt.Errorf("%s 실패 (user=%d, coin=%d): %w", st.name, h.userID, h.coinID, err)
			}
		}
	}
	if len(holdings) > 0 {
		slog.InfoContext(ctx, "상장 폐지 보유분 정리", "holdings", len(holdings))
	}
	// 시세 기록이 없는 코인의 보유분은 정리할 가격이 없어 남겨 둔다 (시즌 정산 때 함께 정리됨)
	var unpriced int
	if err := tx.QueryRowContext(ctx, fmt.Sprintf(`SELECT COUNT(*) FROM user_assets WHERE symbol_id IN (%s)`, in), ids...).
		Scan(&unpriced); err == nil && unpriced > 0 {
		slog.WarnContext(ctx, "시세 기록이 없어 정리하지 못한 상장 폐지 보유분", "count", unpriced)
	}

	// 2. 예약 주문 취소: 사용자·코인별로 알리고 지운다 (Redis 대기열은 백엔드 대조 작업이 지운다)
	rows, err = tx.QueryContext(ctx, fmt.Sprintf(`
		SELECT user_id, symbol_id, COUNT(*) FROM orders
		WHERE status = 'PENDING' AND symbol_id IN (%s)
		GROUP BY user_id, symbol_id`, in), ids...)
	if err != nil {
		return fmt.Errorf("취소할 예약 주문 조회 실패: %w", err)
	}
	type cancelled struct{ userID, coinID, count int }
	var cancels []cancelled
	for rows.Next() {
		var c cancelled
		if err := rows.Scan(&c.userID, &c.coinID, &c.count); err != nil {
			rows.Close()
			return fmt.Errorf("예약 주문 읽기 실패: %w", err)
		}
		cancels = append(cancels, c)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return fmt.Errorf("예약 주문 읽기 실패: %w", err)
	}
	for _, c := range cancels {
		msg := fmt.Sprintf("상장 폐지로 예약 주문이 취소됐어요.\n%s 예약 주문 %d건", names[c.coinID], c.count)
		if _, err := tx.ExecContext(ctx, `INSERT INTO notifications (user_id, message, message_type, is_read) VALUES (?, ?, 'ORDER_EXECUTION', 0)`,
			c.userID, msg); err != nil {
			return fmt.Errorf("예약 취소 알림 실패: %w", err)
		}
	}
	if _, err := tx.ExecContext(ctx, fmt.Sprintf(`DELETE FROM orders WHERE status = 'PENDING' AND symbol_id IN (%s)`, in), ids...); err != nil {
		return fmt.Errorf("예약 주문 취소 실패: %w", err)
	}
	if len(cancels) > 0 {
		slog.InfoContext(ctx, "상장 폐지 예약 주문 취소", "users_coins", len(cancels))
	}
	return nil
}

// sellValue 수량 × 가격을 원 단위로 내림 (받는 돈은 내림: 백엔드 Money.krwFloor와 같은 규칙)
func sellValue(amount, price string) (int64, error) {
	qty, ok := new(big.Rat).SetString(amount)
	if !ok {
		return 0, fmt.Errorf("수량 형식 오류: %q", amount)
	}
	p, ok := new(big.Rat).SetString(price)
	if !ok {
		return 0, fmt.Errorf("가격 형식 오류: %q", price)
	}
	total := new(big.Rat).Mul(qty, p)
	floor := new(big.Int).Quo(total.Num(), total.Denom()) // 양수이므로 몫이 내림
	return floor.Int64(), nil
}

// liquidationMessage 알림 문구: 첫 줄은 제목, 다음 줄은 내용 (알림 목록이 이렇게 나눠 보여 준다)
func liquidationMessage(name, amount, price string, krw int64) string {
	return fmt.Sprintf("상장 폐지로 보유 코인을 정리했어요.\n%s %s개를 개당 %s원에 팔아 %s원을 입금했어요",
		name, trimDecimal(amount), formatPrice(price), formatThousands(krw))
}

// trimDecimal "0.5000000000" → "0.5", "12.00" → "12"
func trimDecimal(v string) string {
	if strings.Contains(v, ".") {
		v = strings.TrimRight(strings.TrimRight(v, "0"), ".")
	}
	return v
}

// formatPrice 100원 이상은 원 단위에 천 단위 구분, 그 아래는 소수 그대로 ("103,621,000", "0.0123")
func formatPrice(price string) string {
	v := trimDecimal(price)
	whole, _, _ := strings.Cut(v, ".")
	n, err := strconv.ParseInt(whole, 10, 64)
	if err != nil || n < 100 {
		return v
	}
	return formatThousands(n)
}

func formatThousands(n int64) string {
	s := strconv.FormatInt(n, 10)
	var b strings.Builder
	for i, r := range s {
		if i > 0 && (len(s)-i)%3 == 0 {
			b.WriteByte(',')
		}
		b.WriteRune(r)
	}
	return b.String()
}

func idsOf(coins []coinRow) []any {
	ids := make([]any, len(coins))
	for i, c := range coins {
		ids[i] = c.id
	}
	return ids
}

func symbolsOf(coins []coinRow) []string {
	out := make([]string, len(coins))
	for i, c := range coins {
		out[i] = c.symbol
	}
	return out
}
