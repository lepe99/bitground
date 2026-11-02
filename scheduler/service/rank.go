package service

import (
	"Bitground-go/model"
	"context"
	"database/sql"
	"fmt"
	"log"
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
// 코인 현재가(시즌 정산의 청산 가격)와 시세(가격 이력 저장용)를 함께 반환합니다.
//
// 총자산 계산과 순위·티어 산정을 SQL 집합 연산 한 번(윈도 함수)으로 처리한다.
// 실행 간 공유하는 임시 테이블·전역 상태가 없어 두 실행이 겹쳐도 서로의 결과를 지우지 않고,
// 랭킹과 스냅샷이 한 트랜잭션에서 같은 시점의 자산으로 계산된다.
func UpdateRank(ctx context.Context, db *sql.DB, symbolMap map[string]int, seasonID int, dailySnapshot bool, now time.Time) (map[int]float64, map[int]model.UpbitCoinPrice, error) {
	coinPrices, coinPriceHistory, err := getAllCoinsCurrentPrice(ctx, symbolMap)
	if err != nil {
		return nil, nil, fmt.Errorf("코인 현재가 조회 실패: %w", err)
	}
	prices, err := pricesJSON(coinPrices)
	if err != nil {
		return nil, nil, err
	}

	txCtx, cancel := context.WithTimeout(ctx, 60*time.Second)
	defer cancel()

	err = withTx(txCtx, db, func(tx *sql.Tx) error {
		n, err := upsertRankings(txCtx, tx, seasonID, prices)
		if err != nil {
			return err
		}
		log.Printf("랭킹 - 시즌 %d 랭킹 갱신 (영향 행 %d)\n", seasonID, n)

		if dailySnapshot {
			n, err := upsertDailyBalances(txCtx, tx, seasonID, prices, now)
			if err != nil {
				return err
			}
			log.Printf("랭킹 - 일일 자산 스냅샷 저장 (영향 행 %d)\n", n)
		}
		return nil
	})
	if err != nil {
		return nil, nil, err
	}

	return coinPrices, coinPriceHistory, nil
}

// upsertRankings 총자산 내림차순으로 순위를 매기고 순위 구간에 따라 티어를 정합니다.
//   - 1위: 7, 2~3위: 6
//   - 나머지는 4위 이하 인원 중 상위 비율로 10%: 5, 25%: 4, 45%: 3, 70%: 2, 그 외: 1
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
		       CASE
		           WHEN ranks = 1 THEN 7
		           WHEN ranks <= 3 THEN 6
		           WHEN (ranks - 3) / GREATEST(participants - 3, 1.0) <= 0.10 THEN 5
		           WHEN (ranks - 3) / GREATEST(participants - 3, 1.0) <= 0.25 THEN 4
		           WHEN (ranks - 3) / GREATEST(participants - 3, 1.0) <= 0.45 THEN 3
		           WHEN (ranks - 3) / GREATEST(participants - 3, 1.0) <= 0.70 THEN 2
		           ELSE 1
		       END
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

func getAllCoinsCurrentPrice(ctx context.Context, symbolMap map[string]int) (map[int]float64, map[int]model.UpbitCoinPrice, error) {
	apiURL := "https://api.upbit.com/v1/ticker/all?quote_currencies=KRW"

	var upbitPrices []model.UpbitCoinPrice
	if err := getJSON(ctx, apiURL, 10*time.Second, &upbitPrices); err != nil {
		return nil, nil, err
	}

	coinPrices := make(map[int]float64)
	coinPriceHistory := make(map[int]model.UpbitCoinPrice)

	for _, upbitCoin := range upbitPrices {
		symbol := upbitCoin.Market
		if symbolId, exists := symbolMap[symbol]; exists {
			coinPrices[symbolId] = upbitCoin.TradePrice
			coinPriceHistory[symbolId] = upbitCoin
		}
	}

	return coinPrices, coinPriceHistory, nil
}

// GetCurrentSeasonID 진행중인 시즌 ID 가져오기
func GetCurrentSeasonID(ctx context.Context, db *sql.DB) (int, error) {
	// 쿼리 타임아웃 설정 (10초)
	queryCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	query := `
		SELECT id
		FROM seasons
		WHERE status = 'PENDING'
		LIMIT 1
	`

	// 쿼리 실행
	var seasonID int
	err := db.QueryRowContext(queryCtx, query).Scan(&seasonID)
	if err != nil {
		return 0, fmt.Errorf("쿼리 실행 에러: %w", err)
	}

	return seasonID, nil
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
			log.Printf("행 닫기 에러: %v\n", err)
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
