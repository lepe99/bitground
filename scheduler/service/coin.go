package service

import (
	"Bitground-go/model"
	"context"
	"database/sql"
	"fmt"
	"golang.org/x/sync/errgroup"
	"log/slog"
	"time"
)

func UpdateCoins(ctx context.Context, db *sql.DB) error {
	// 고루틴 사용
	var coinList []model.UpbitCoinList
	var coinDetailList map[string]model.UpbitCoinPrice

	g, gCtx := errgroup.WithContext(ctx)
	// 1. API에서 코인 정보를 가져와 "KRW-"로 시작하는 코인 심볼을 필터링 (비동기)
	g.Go(func() error {
		symbols, err := getSymbolList(gCtx) // gCtx를 사용
		if err != nil {
			return fmt.Errorf("getSymbolList 에러: %w", err)
		}
		coinList = symbols // 결과 저장
		return nil
	})

	// 2. 심볼에 해당하는 코인 정보들 가져오기 (상장 폐지 표시는 매시간 SyncDelistings가 맡는다)
	g.Go(func() error {
		details, err := getCoinDetails(gCtx) // gCtx를 사용
		if err != nil {
			return fmt.Errorf("getCoinDetails 에러: %w", err)
		}
		coinDetailList = details // 결과 저장
		return nil
	})

	if err := g.Wait(); err != nil {
		// 에러가 발생하면 여기서 처리하고 함수를 종료합니다.
		return fmt.Errorf("고루틴 수행 중 에러 발생: %w", err)
	}

	// 4. 데이터들을 가공하여 db에 저장할 수 있는 형태로 변환
	coinSymbols := prepareCoinSymbols(coinList, coinDetailList)

	// 5. db에 코인 심볼 목록 저장
	if err := insertCoinSymbols(ctx, db, coinSymbols); err != nil {
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

	// 트랜잭션 시작
	tx, err := db.BeginTx(queryCtx, nil)
	if err != nil {
		return fmt.Errorf("트랜잭션 시작 에러: %w", err)
	}
	defer func() {
		if p := recover(); p != nil {
			_ = tx.Rollback()
			panic(p)
		} else if err != nil {
			_ = tx.Rollback()
		}
	}()

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
	defer func() {
		if err := stmt.Close(); err != nil {
			slog.WarnContext(ctx, "쿼리 닫기 실패", "error", err)
		}
	}()

	for _, symbol := range coinSymbols {
		if _, err := stmt.ExecContext(queryCtx,
			symbol.Symbol, symbol.KoreanName, symbol.TradePrice,
			symbol.ChangeRate, symbol.IsCaution, symbol.IsWarning); err != nil {
			return fmt.Errorf("코인 심볼 삽입/업데이트 에러: %w", err)
		}
	}

	return tx.Commit() // 트랜잭션 커밋
}
