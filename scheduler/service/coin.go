package service

import (
	"Bitground-go/model"
	"context"
	"database/sql"
	"fmt"
	"time"
)

// FetchMarkets 업비트 원화 마켓 목록 (코인 이름, 유의·주의 지정). 외부 조회만 한다 (수집 단계)
func FetchMarkets(ctx context.Context) ([]model.UpbitCoinList, error) {
	return getSymbolList(ctx)
}

// FetchTickers 업비트 원화 전 종목 시세 (심볼 → 시세). 코인 목록·상장 폐지·랭킹·가격 기록이 함께 쓴다
func FetchTickers(ctx context.Context) (map[string]model.UpbitCoinPrice, error) {
	return getCoinDetails(ctx)
}

// SaveCoins 마켓 목록과 시세로 코인 목록을 갱신한다 (상장 폐지 표시는 ApplyDelistings가 맡는다)
func SaveCoins(ctx context.Context, db *sql.DB, markets []model.UpbitCoinList, tickers map[string]model.UpbitCoinPrice) error {
	if err := insertCoinSymbols(ctx, db, prepareCoinSymbols(markets, tickers)); err != nil {
		return fmt.Errorf("insertCoinSymbols 에러: %w", err)
	}
	return nil
}

// api에서 코인 전체 정보를 가져와 "KRW-" 로 시작하는 코인 심볼을 필터링합니다.
func getSymbolList(ctx context.Context) ([]model.UpbitCoinList, error) {
	apiURL := "https://api.upbit.com/v1/market/all?is_details=true"

	var coins []model.UpbitCoinList
	if err := getJSON(ctx, apiURL, 10*time.Second, &coins); err != nil {
		return nil, err
	}

	// "KRW-"로 시작하는 코인 심볼 필터링
	var filteredCoins []model.UpbitCoinList
	for _, coin := range coins {
		if len(coin.Market) >= 4 && coin.Market[:4] == "KRW-" {
			filteredCoins = append(filteredCoins, coin)
		}
	}

	return filteredCoins, nil

}

// api에서 코인별 정보를 가져와 정보를 구체화합니다.
func getCoinDetails(ctx context.Context) (map[string]model.UpbitCoinPrice, error) {
	apiURL := "https://api.upbit.com/v1/ticker/all?quote_currencies=KRW"

	var coinDetails []model.UpbitCoinPrice
	if err := getJSON(ctx, apiURL, 10*time.Second, &coinDetails); err != nil {
		return nil, err
	}

	// 코인 심볼을 키로 하는 맵 생성
	coinMap := make(map[string]model.UpbitCoinPrice)
	for _, coin := range coinDetails {
		coinMap[coin.Market] = coin
	}

	return coinMap, nil
}

// 데이터들을 가공하여 db에 저장할 수 있는 형태로 변환합니다.
func prepareCoinSymbols(coinList []model.UpbitCoinList, coinDetailList map[string]model.UpbitCoinPrice) []model.CoinSymbol {
	var coinSymbols []model.CoinSymbol

	for _, coin := range coinList {
		if detail, exists := coinDetailList[coin.Market]; exists {
			// 코인 심볼 구조체 생성
			symbol := model.CoinSymbol{
				Symbol:     coin.Market,
				KoreanName: coin.KoreanName,
				TradePrice: int64(detail.AccTradePrice),
				IsCaution: coin.MarketEvent.Caution.PriceFluctuations || coin.MarketEvent.Caution.TradingVolumeSoaring ||
					coin.MarketEvent.Caution.DepositAmountSoaring || coin.MarketEvent.Caution.GlobalPriceDifferences ||
					coin.MarketEvent.Caution.ConcentrationOfSmallAccounts,
				IsWarning: coin.MarketEvent.Warning,
			}

			// ChangeRate 계산
			if detail.PrevClosingPrice != 0 {
				symbol.ChangeRate = (detail.TradePrice - detail.PrevClosingPrice) / detail.PrevClosingPrice * 100
			}

			coinSymbols = append(coinSymbols, symbol)
		}
	}

	return coinSymbols
}

// db에 코인 심볼 목록을 저장합니다.
func insertCoinSymbols(ctx context.Context, db *sql.DB, coinSymbols []model.CoinSymbol) error {

	// 쿼리 타임아웃 설정 (10초)
	queryCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()

	// 실패하면 withTx가 롤백한다 (예전: err가 이름 있는 반환값이 아니어서 삽입 실패 시 지연 롤백이 돌지 않고
	// 컨텍스트 취소에 기대고 있었다)
	return withTx(queryCtx, db, func(tx *sql.Tx) error {
		query := `
		INSERT INTO coins (symbol, korean_name, trade_price_24h, change_rate, is_caution, is_warning)
		VALUES (?, ?, ?, ?, ?, ?)
		ON DUPLICATE KEY UPDATE
			korean_name = VALUES(korean_name),
			trade_price_24h = VALUES(trade_price_24h),
			change_rate = VALUES(change_rate),
			is_caution = VALUES(is_caution),
			is_warning = VALUES(is_warning)
	`
		stmt, err := tx.PrepareContext(queryCtx, query)
		if err != nil {
			return fmt.Errorf("쿼리 준비 에러: %w", err)
		}
		defer closeStmt(ctx, stmt)

		for _, symbol := range coinSymbols {
			if _, err := stmt.ExecContext(queryCtx,
				symbol.Symbol, symbol.KoreanName, symbol.TradePrice,
				symbol.ChangeRate, symbol.IsCaution, symbol.IsWarning); err != nil {
				return fmt.Errorf("코인 심볼 삽입/업데이트 에러: %w", err)
			}
		}
		return nil
	})
}
