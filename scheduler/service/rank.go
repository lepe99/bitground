package service

import (
	"Bitground-go/model"
	"context"
	"database/sql"
	"fmt"
	"log/slog"
	"time"
)

// userTotalsCTE 시즌 참여자별 총자산 = 현금 + Σ(보유 수량 × 현재가)
//
// 랭킹 대상은 이번 시즌에 체결된 주문이 1건 이상 있는, 탈퇴하지 않은 사용자다.
// 가입만 하고 거래하지 않은 사용자는 랭킹·티어 산정에서 빠진다(시즌 정산 때 티어 0).
// 가격 정보가 없는 코인(상장 폐지 등)은 0원으로 계산한다.
//
// 파라미터: 코인 가격 JSON(pricesJSON), 시즌 ID
const userTotalsCTE = `
	WITH prices AS (
		SELECT p.coin_id, p.price FROM ` + pricesTable + ` p
	),
	participants AS (
		SELECT DISTINCT user_id FROM orders WHERE season_id = ? AND status = 'COMPLETED'
	),
	totals AS (
		SELECT u.id AS user_id,
		       u.cash,
		       FLOOR(u.cash + COALESCE(SUM(ua.amount * p.price), 0)) AS total_value
		FROM participants pt
		JOIN users u ON u.id = pt.user_id AND u.is_deleted = 0
		LEFT JOIN user_assets ua ON ua.user_id = u.id
		LEFT JOIN prices p ON p.coin_id = ua.symbol_id
		GROUP BY u.id, u.cash
	)`

// UpdateRank 현재 시즌 랭킹을 다시 계산합니다. dailySnapshot이면 일일 자산 스냅샷도 남깁니다.
// coinPrices 코인 ID → 현재가 (CoinPrices로 만든다. 시즌 정산의 청산 가격과 같은 값)
//
// 총자산 계산과 순위·티어 산정을 SQL 집합 연산 한 번(윈도 함수)으로 처리한다.
// 실행 간 공유하는 임시 테이블·전역 상태가 없어 두 실행이 겹쳐도 서로의 결과를 지우지 않는다.
// 거래 중에도 매시 돌므로 원본(주문·자산·사용자)을 잠그지 않도록 READ COMMITTED로 실행한다 (readCommitted, db.go).
func UpdateRank(ctx context.Context, db *sql.DB, coinPrices map[int]float64, seasonID int, dailySnapshot bool, now time.Time) error {
	prices, err := pricesJSON(coinPrices)
	if err != nil {
		return err
	}

	txCtx, cancel := context.WithTimeout(ctx, 60*time.Second)
	defer cancel()

	err = withTxOptions(txCtx, db, readCommitted, func(tx *sql.Tx) error {
		n, err := upsertRankings(txCtx, tx, seasonID, prices)
		if err != nil {
			return err
		}
		slog.InfoContext(ctx, "랭킹 갱신", "season_id", seasonID, "rows", n)

		if dailySnapshot {
			n, err := upsertDailyBalances(txCtx, tx, seasonID, prices, now)
			if err != nil {
				return err
			}
			slog.InfoContext(ctx, "일일 자산 스냅샷 저장", "rows", n)
		}
		return nil
	})
	return err
}

// upsertRankings 총자산 내림차순으로 순위를 매기고 순위 구간에 따라 티어를 정합니다.
//   - 1위: 7, 2~3위: 6
//   - 나머지는 4위 이하 인원 중 상위 비율로 정한다 (tierCutoffs, rules.go)
func upsertRankings(ctx context.Context, tx *sql.Tx, seasonID int, prices string) (int64, error) {
	// 시즌 중 탈퇴한 사용자는 랭킹에서 지운다
	_, err := tx.ExecContext(ctx, `
		DELETE ur FROM user_rankings ur
		JOIN users u ON u.id = ur.user_id
		WHERE ur.season_id = ? AND u.is_deleted = 1`, seasonID)
	if err != nil {
		return 0, fmt.Errorf("탈퇴 사용자 랭킹 삭제 실패: %w", err)
	}

	query := `
		INSERT INTO user_rankings (season_id, user_id, total_value, ranks, tier)` + userTotalsCTE + `
		SELECT ?, user_id, total_value, ranks,
		       ` + tierCaseSQL() + `
		FROM (
		    SELECT user_id,
		           total_value,
		           ROW_NUMBER() OVER (ORDER BY total_value DESC, user_id) AS ranks,
		           COUNT(*) OVER () AS participants
		    FROM totals
		) AS ranked
		ON DUPLICATE KEY UPDATE
		    total_value = VALUES(total_value),
		    ranks = VALUES(ranks),
		    tier = VALUES(tier)`

	res, err := tx.ExecContext(ctx, query, prices, seasonID, seasonID)
	if err != nil {
		return 0, fmt.Errorf("랭킹 계산 실패: %w", err)
	}
	return rowsAffected(res), nil
}

// upsertDailyBalances 참여자별 현금·코인 평가액·총자산을 오늘 날짜로 남깁니다. (투자 내역 그래프용)
func upsertDailyBalances(ctx context.Context, tx *sql.Tx, seasonID int, prices string, now time.Time) (int64, error) {
	query := `
		INSERT INTO user_daily_balances (user_id, cash_balance, coin_holdings_value, total_value, season_id, snapshot_date)` + userTotalsCTE + `
		SELECT user_id, cash, total_value - cash, total_value, ?, ?
		FROM totals
		ON DUPLICATE KEY UPDATE
		    cash_balance = VALUES(cash_balance),
		    coin_holdings_value = VALUES(coin_holdings_value),
		    total_value = VALUES(total_value)`

	res, err := tx.ExecContext(ctx, query, prices, seasonID, seasonID, now.Format(time.DateOnly))
	if err != nil {
		return 0, fmt.Errorf("일일 자산 스냅샷 저장 실패: %w", err)
	}
	return rowsAffected(res), nil
}

// rowsAffected 로그용 영향 행 수. ON DUPLICATE KEY UPDATE는 새 행 1, 갱신 2, 변화 없음 0으로 센다.
func rowsAffected(res sql.Result) int64 {
	n, err := res.RowsAffected()
	if err != nil {
		return -1
	}
	return n
}

// CoinPrices 업비트 시세(심볼 기준)를 DB 코인 ID 기준 현재가와 가격 기록용 시세로 바꾼다 (DB에 있는 코인만)
func CoinPrices(symbolMap map[string]int, tickers map[string]model.UpbitCoinPrice) (map[int]float64, map[int]model.UpbitCoinPrice) {
	prices := make(map[int]float64, len(symbolMap))
	history := make(map[int]model.UpbitCoinPrice, len(symbolMap))
	for symbol, ticker := range tickers {
		if id, ok := symbolMap[symbol]; ok {
			prices[id] = ticker.TradePrice
			history[id] = ticker
		}
	}
	return prices, history
}

// GetActiveCoinsSymbols 데이터베이스에서 활성화된 코인 심볼들을 가져오는 함수
func GetActiveCoinsSymbols(ctx context.Context, db *sql.DB) (map[string]int, error) {
	// 쿼리 타임아웃 설정 (10초)
	queryCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	query := `
       SELECT id, symbol
       FROM coins
       WHERE is_deleted = 0
       ORDER BY symbol;
   `

	rows, err := db.QueryContext(queryCtx, query)
	if err != nil {
		return nil, fmt.Errorf("쿼리 실행 에러: %w", err)
	}
	defer func(rows *sql.Rows) {
		if err := rows.Close(); err != nil {
			slog.WarnContext(ctx, "행 닫기 실패", "error", err)
		}
	}(rows)

	symbolMap := make(map[string]int)
	for rows.Next() {
		var coinSymbol model.CoinSymbol
		err := rows.Scan(&coinSymbol.Id, &coinSymbol.Symbol)
		if err != nil {
			return nil, fmt.Errorf("행 스캔 에러: %w", err)
		}
		symbolMap[coinSymbol.Symbol] = coinSymbol.Id
	}

	if err = rows.Err(); err != nil {
		return nil, fmt.Errorf("행 반복 에러: %w", err)
	}

	return symbolMap, nil
}
