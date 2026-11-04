package service

import (
	"context"
	"fmt"
	"math"
	"net/url"
	"sort"
	"strings"
	"time"
)

// 일일 인사이트에 들어가는 시세 데이터. 모델이 기억으로 시장을 지어내지 않도록 코드가 업비트에서 직접 모아 넘긴다.

const (
	// 고정 대상 외에 자동으로 고를 코인 수 (화면 버튼이 가로 스크롤 없이 들어가도록 시장 전체 포함 6개)
	extraInsightCoins = 4
	// 자동 선정 후보의 최소 24시간 거래대금 (너무 한산한 코인은 등락률만 커서 제외)
	minInsightVolume = 1_000_000_000
)

// 매일 분석하는 고정 대상
var fixedInsightCoins = []string{"KRW-BTC"}

// 가격이 원화에 묶여 있어 거래대금만 크고 분석할 거리가 없는 스테이블코인 (자동 선정에서 제외)
var stableCoins = map[string]bool{
	"KRW-USDT": true, "KRW-USDC": true, "KRW-USDS": true, "KRW-USDE": true, "KRW-USD1": true, "KRW-DAI": true, "KRW-PYUSD": true,
}

// upbitTicker 업비트 현재가 (ticker/all)
type upbitTicker struct {
	Market           string  `json:"market"`
	TradePrice       float64 `json:"trade_price"`
	SignedChangeRate float64 `json:"signed_change_rate"` // 전일 종가(오전 9시 기준) 대비, 0.05 = 5%
	AccTradePrice24h float64 `json:"acc_trade_price_24h"`
	HighPrice        float64 `json:"high_price"`
	LowPrice         float64 `json:"low_price"`
}

// upbitDayCandle 업비트 일봉 (최신이 앞)
type upbitDayCandle struct {
	TradePrice    float64 `json:"trade_price"`
	AccTradePrice float64 `json:"candle_acc_trade_price"`
}

// marketSnapshot 우리 서비스에 상장된 원화 코인의 현재 시세·이름·유의/주의 지정
type marketSnapshot struct {
	Tickers map[string]upbitTicker
	Names   map[string]string
	Warning map[string]bool
	Caution map[string]bool
}

// insightTarget 분석 대상과 고른 이유 (화면에 함께 표시)
type insightTarget struct {
	Symbol string
	Reason string
}

// coinData 코인 하나의 프롬프트 입력
type coinData struct {
	Symbol      string
	Name        string
	Reason      string
	Price       float64
	Change      float64 // 전일 종가 대비 등락률 (%)
	High, Low   float64
	Volume24h   float64
	VolumeRatio float64 // 24시간 거래대금 ÷ 최근 7일 평균 일 거래대금 (0이면 모름)
	Change7d    float64 // 7일 전 종가 대비 등락률 (%)
	Has7d       bool
	Warning     bool
	Caution     bool
	Mood        string
}

// marketData 시장 전체 프롬프트 입력
type marketData struct {
	Listed, Up, Down   int
	MedianChange       float64
	BTC, ETH           float64
	TopVolume          []coinData // 거래대금 상위 5
	Warnings, Cautions int
	Mood               string
}

// collectMarketSnapshot 상장 목록(이름·지정 여부)과 전체 현재가를 한 번씩 받아 우리 상장 코인만 남긴다
func collectMarketSnapshot(ctx context.Context, symbolMap map[string]int) (marketSnapshot, error) {
	list, err := getSymbolList(ctx)
	if err != nil {
		return marketSnapshot{}, fmt.Errorf("업비트 상장 목록 조회 실패: %w", err)
	}
	var tickers []upbitTicker
	if err := getJSON(ctx, "https://api.upbit.com/v1/ticker/all?quote_currencies=KRW", 10*time.Second, &tickers); err != nil {
		return marketSnapshot{}, fmt.Errorf("업비트 현재가 조회 실패: %w", err)
	}

	snap := marketSnapshot{
		Tickers: map[string]upbitTicker{},
		Names:   map[string]string{},
		Warning: map[string]bool{},
		Caution: map[string]bool{},
	}
	for _, c := range list {
		if _, ok := symbolMap[c.Market]; !ok {
			continue
		}
		snap.Names[c.Market] = c.KoreanName
		snap.Warning[c.Market] = c.MarketEvent.Warning
		ev := c.MarketEvent.Caution
		snap.Caution[c.Market] = ev.PriceFluctuations || ev.TradingVolumeSoaring || ev.DepositAmountSoaring ||
			ev.GlobalPriceDifferences || ev.ConcentrationOfSmallAccounts
	}
	for _, t := range tickers {
		if _, ok := snap.Names[t.Market]; ok {
			snap.Tickers[t.Market] = t
		}
	}
	return snap, nil
}

// selectInsightTargets 비트코인 + 자동 4종. 자동 선정은 거래대금 상위 2, 상승률 1위, 하락률 1위이고
// 남는 자리(오르거나 내린 코인이 없는 날)는 거래대금 순으로 채운다. 이 순서가 화면 버튼 순서가 된다
func selectInsightTargets(snap marketSnapshot) []insightTarget {
	var targets []insightTarget
	picked := map[string]bool{}
	add := func(symbol, reason string) bool {
		if picked[symbol] {
			return false
		}
		picked[symbol] = true
		targets = append(targets, insightTarget{Symbol: symbol, Reason: reason})
		return true
	}

	for _, s := range fixedInsightCoins {
		if _, ok := snap.Tickers[s]; ok {
			add(s, "주요 코인")
		}
	}

	var candidates []upbitTicker
	for s, t := range snap.Tickers {
		if !picked[s] && !stableCoins[s] && t.AccTradePrice24h >= minInsightVolume {
			candidates = append(candidates, t)
		}
	}
	byVolume := sortedTickers(candidates, func(a, b upbitTicker) bool { return a.AccTradePrice24h > b.AccTradePrice24h })
	byChangeDesc := sortedTickers(candidates, func(a, b upbitTicker) bool { return a.SignedChangeRate > b.SignedChangeRate })
	byChangeAsc := sortedTickers(candidates, func(a, b upbitTicker) bool { return a.SignedChangeRate < b.SignedChangeRate })

	extra := 0
	pick := func(list []upbitTicker, reason string, n int, filter func(upbitTicker) bool) {
		for _, t := range list {
			if n == 0 || extra >= extraInsightCoins {
				return
			}
			if filter != nil && !filter(t) {
				continue
			}
			if add(t.Market, reason) {
				n--
				extra++
			}
		}
	}
	pick(byVolume, "거래대금 상위", 2, nil)
	pick(byChangeDesc, "상승률 상위", 1, func(t upbitTicker) bool { return t.SignedChangeRate > 0 })
	pick(byChangeAsc, "하락률 상위", 1, func(t upbitTicker) bool { return t.SignedChangeRate < 0 })
	pick(byVolume, "거래대금 상위", extraInsightCoins, nil)
	return targets
}

func sortedTickers(list []upbitTicker, less func(a, b upbitTicker) bool) []upbitTicker {
	out := append([]upbitTicker(nil), list...)
	sort.SliceStable(out, func(i, j int) bool {
		if less(out[i], out[j]) {
			return true
		}
		if less(out[j], out[i]) {
			return false
		}
		return out[i].Market < out[j].Market // 같으면 심볼 순으로 고정 (실행마다 결과가 같도록)
	})
	return out
}

// fetchDayCandles 최근 8개 일봉 (오늘 진행 중인 봉 + 지난 7일).
// 0시에는 랭킹·코인·마켓 인덱스 작업도 같은 때 업비트를 불러 요청 한도(429)에 걸릴 수 있어 잠시 뒤 두 번까지 다시 시도한다
func fetchDayCandles(ctx context.Context, market string) ([]upbitDayCandle, error) {
	u := "https://api.upbit.com/v1/candles/days?count=8&market=" + url.QueryEscape(market)
	var err error
	for attempt := 0; attempt < 3; attempt++ {
		if attempt > 0 {
			select {
			case <-time.After(time.Duration(attempt) * time.Second):
			case <-ctx.Done():
				return nil, ctx.Err()
			}
		}
		var candles []upbitDayCandle
		if err = getJSON(ctx, u, 10*time.Second, &candles); err == nil {
			return candles, nil
		}
		if !strings.Contains(err.Error(), "429") {
			return nil, err
		}
	}
	return nil, err
}

// buildCoinData 현재가와 일봉으로 코인 입력을 만든다. 일봉이 없으면 7일 지표만 비운다
func buildCoinData(target insightTarget, snap marketSnapshot, candles []upbitDayCandle) coinData {
	t := snap.Tickers[target.Symbol]
	d := coinData{
		Symbol:    target.Symbol,
		Name:      snap.Names[target.Symbol],
		Reason:    target.Reason,
		Price:     t.TradePrice,
		Change:    round2(t.SignedChangeRate * 100),
		High:      t.HighPrice,
		Low:       t.LowPrice,
		Volume24h: t.AccTradePrice24h,
		Warning:   snap.Warning[target.Symbol],
		Caution:   snap.Caution[target.Symbol],
	}
	if len(candles) >= 8 {
		var sum float64
		for _, c := range candles[1:8] {
			sum += c.AccTradePrice
		}
		if avg := sum / 7; avg > 0 {
			d.VolumeRatio = math.Round(t.AccTradePrice24h/avg*10) / 10
		}
		if base := candles[7].TradePrice; base > 0 {
			d.Change7d = round2((t.TradePrice/base - 1) * 100)
			d.Has7d = true
		}
	}
	d.Mood = moodFor(d.Change)
	return d
}

// buildMarketData 상장 코인 전체의 등락 분포와 거래대금 상위
func buildMarketData(snap marketSnapshot) marketData {
	m := marketData{Listed: len(snap.Tickers)}
	var changes []float64
	var all []upbitTicker
	for s, t := range snap.Tickers {
		c := t.SignedChangeRate * 100
		changes = append(changes, c)
		all = append(all, t)
		switch {
		case c > 0:
			m.Up++
		case c < 0:
			m.Down++
		}
		if snap.Warning[s] {
			m.Warnings++
		}
		if snap.Caution[s] {
			m.Cautions++
		}
	}
	m.MedianChange = round2(median(changes))
	m.BTC = round2(snap.Tickers["KRW-BTC"].SignedChangeRate * 100)
	m.ETH = round2(snap.Tickers["KRW-ETH"].SignedChangeRate * 100)
	for i, t := range sortedTickers(all, func(a, b upbitTicker) bool { return a.AccTradePrice24h > b.AccTradePrice24h }) {
		if i == 5 {
			break
		}
		m.TopVolume = append(m.TopVolume, buildCoinData(insightTarget{Symbol: t.Market}, snap, nil))
	}
	m.Mood = moodFor(m.MedianChange)
	return m
}

// moodFor 등락률(%)로 정하는 분위기 라벨. 모델이 아니라 이 규칙이 정한다
func moodFor(change float64) string {
	switch {
	case change >= 5:
		return "강세"
	case change >= 1.5:
		return "약간 강세"
	case change > -1.5:
		return "중립"
	case change > -5:
		return "약간 약세"
	default:
		return "약세"
	}
}

// moodScore 기존 화면·컬럼(score)과의 호환용 0~100 값: 50 + 등락률 × 5
func moodScore(change float64) int {
	return int(math.Max(0, math.Min(100, math.Round(50+change*5))))
}

func median(values []float64) float64 {
	if len(values) == 0 {
		return 0
	}
	s := append([]float64(nil), values...)
	sort.Float64s(s)
	if len(s)%2 == 1 {
		return s[len(s)/2]
	}
	return (s[len(s)/2-1] + s[len(s)/2]) / 2
}

func round2(v float64) float64 {
	return math.Round(v*100) / 100
}
