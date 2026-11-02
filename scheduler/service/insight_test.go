package service

import "testing"

func TestFilterInvalidInsights(t *testing.T) {
	symbolMap := map[string]int{"KRW-BTC": 1, "KRW-DOGE": 2}
	insights := []Insight{
		{Symbol: "MARKET_OVERALL"}, {Symbol: "KRW-ETH"}, {Symbol: "KRW-SOL"}, {Symbol: "KRW-XRP"},
		{Symbol: "KRW-DOGE"},
		{Symbol: "KRW-NOT-LISTED"}, // 모델이 지어낸 심볼은 버린다
	}

	got := filterInvalidInsights(insights, symbolMap)

	var symbols []string
	for _, in := range got {
		symbols = append(symbols, in.Symbol)
	}
	want := []string{"MARKET_OVERALL", "KRW-ETH", "KRW-SOL", "KRW-XRP", "KRW-DOGE"}
	if len(symbols) != len(want) {
		t.Fatalf("filterInvalidInsights() = %v, want %v", symbols, want)
	}
	for i := range want {
		if symbols[i] != want[i] {
			t.Errorf("filterInvalidInsights()[%d] = %s, want %s", i, symbols[i], want[i])
		}
	}
}

func TestCalcMarketIndex(t *testing.T) {
	caps := []GeckoCoin{{Symbol: "btc", MarketCap: 2_000_000_000_000}, {Symbol: "eth", MarketCap: 500_000_000_000}}
	market, alt := calcMarketIndex(caps)
	if market != 25000 || alt != 5000 {
		t.Errorf("calcMarketIndex() = (%d, %d), want (25000, 5000)", market, alt)
	}
}
