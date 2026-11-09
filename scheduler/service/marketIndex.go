package service

import (
	"context"
	"database/sql"
	"fmt"
	"time"
)

// GeckoCoin API에서 사용하는 코인 마켓 캡 정보를 나타내는 구조체
type GeckoCoin struct {
	Symbol    string `json:"symbol"`
	MarketCap int64  `json:"market_cap"`
}

// UpdateMarketIndex 함수는 상위 10개 코인의 마켓 캡 정보를 가져와서 마켓 인덱스와 알트 인덱스를 계산하고, 이를 데이터베이스에 삽입합니다.
// coingeckoKey 무료 Demo 키(비우면 키 없이 호출). 키 없는 호출은 IP 기준 제한이라 Lambda처럼 IP를 나눠 쓰면 막힐 수 있다
// now 실행 기준 정시 (이 시각의 행으로 남긴다)
func UpdateMarketIndex(ctx context.Context, db *sql.DB, coingeckoKey string, now time.Time) error {
	// 1. CoinGecko API를 사용하여 상위 10개 코인의 마켓 캡 정보를 가져옵니다.
	coinCaps, err := getMarketCap(ctx, coingeckoKey)
	if err != nil {
		return fmt.Errorf("getMarketCap 에러: %w", err)
	}

	// 2. 마켓 인덱스와 알트 인덱스를 계산합니다.
	marketIndex, altIndex := calcMarketIndex(coinCaps)

	// 3. 데이터베이스에 마켓 인덱스와 알트 인덱스를 삽입합니다.
	if err := insertMarketIndex(ctx, db, marketIndex, altIndex, now); err != nil {
		return fmt.Errorf("insertMarketIndex 에러: %w", err)
	}

	return nil
}

// getMarketCap 함수는 CoinGecko API를 사용하여 상위 10개 코인의 마켓 캡 정보를 가져옵니다.
func getMarketCap(ctx context.Context, apiKey string) ([]GeckoCoin, error) {
	apiURL := "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=50"

	// 제외할 종목 심볼 목록 (string 배열)
	excludedSymbols := []string{
		"usdt",
		"bnb",
		"usdc",
		"steth",
		"wbtc",
		"wsteth",
		"leo",
		"usds",
		"weth",
		"weeth",
		"bsc-usd",
		"bgb",
		"usde",
		"cbbtc",
		"wbt",
		"okb",
		"jitosol",
		"susde",
		"tkx",
		"buidl",
		"ondo",
		"cro",
	}

	var coins []GeckoCoin
	var headers map[string]string
	if apiKey != "" {
		headers = map[string]string{"x-cg-demo-api-key": apiKey} // https://docs.coingecko.com/docs/keyless-public-api
	}
	if err := getJSONWithHeaders(ctx, apiURL, 30*time.Second, headers, &coins); err != nil {
		return nil, err
	}

	// 제외할 종목 필터링
	var filteredCoins []GeckoCoin
	for _, coin := range coins {
		isExcluded := false
		for _, excludedSymbol := range excludedSymbols {
			if coin.Symbol == excludedSymbol {
				isExcluded = true
				break
			}
		}
		if !isExcluded {
			filteredCoins = append(filteredCoins, coin)
		}
	}

	// 상위 10개 종목 저장
	top10Coins := make([]GeckoCoin, 0, 10)
	for i, coin := range filteredCoins {
		if i >= 10 {
			break
		}
		top10Coins = append(top10Coins, coin)
	}

	// 결과 반환
	return top10Coins, nil
}

// calcMarketIndex 함수는 상위 10개 코인의 마켓 캡 정보를 기반으로 마켓 인덱스와 알트 인덱스를 계산합니다.
func calcMarketIndex(coinCaps []GeckoCoin) (int, int) {
	marketIndex := 0
	altIndex := 0
	for i, coinCap := range coinCaps {
		if i > 0 {
			altIndex += int(coinCap.MarketCap)
		}
		marketIndex += int(coinCap.MarketCap)
	}
	marketIndex /= 100000000
	altIndex /= 100000000
	return marketIndex, altIndex
}

// insertMarketIndex 함수는 계산된 마켓 인덱스와 알트 인덱스를 데이터베이스에 삽입합니다.
// (기존: 실제 현재 시각을 반올림해, 14시 실행이 30분 넘게 늦거나 재시도되면 15시 행에 들어갔다)
func insertMarketIndex(ctx context.Context, db *sql.DB, marketIndex, altIndex int, now time.Time) error {
	date := now.Format(time.DateOnly)
	hour := now.Hour()

	// 데이터베이스에 마켓 인덱스와 알트 인덱스를 업데이트하는 쿼리
	query := `
		INSERT INTO market_indices (date, hour, market_index, alt_index)
		VALUES (?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE
    		market_index = VALUES(market_index),
    		alt_index = VALUES(alt_index);
	`
	// Context를 활용한 쿼리 실행 (타임아웃: 10초)
	queryCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	_, err := db.ExecContext(queryCtx, query, date, hour, marketIndex, altIndex)
	if err != nil {
		return fmt.Errorf("쿼리 실행 에러: %w", err)
	}

	return nil
}
