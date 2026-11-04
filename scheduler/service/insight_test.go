package service

import (
	"context"
	"strings"
	"testing"
	"time"
)

func TestCalcMarketIndex(t *testing.T) {
	caps := []GeckoCoin{{Symbol: "btc", MarketCap: 2_000_000_000_000}, {Symbol: "eth", MarketCap: 500_000_000_000}}
	market, alt := calcMarketIndex(caps)
	if market != 25000 || alt != 5000 {
		t.Errorf("calcMarketIndex() = (%d, %d), want (25000, 5000)", market, alt)
	}
}

func ticker(market string, changePct, volume float64) upbitTicker {
	return upbitTicker{Market: market, TradePrice: 1000, SignedChangeRate: changePct / 100, AccTradePrice24h: volume}
}

func TestSelectInsightTargets(t *testing.T) {
	snap := marketSnapshot{Tickers: map[string]upbitTicker{}, Names: map[string]string{}, Warning: map[string]bool{}, Caution: map[string]bool{}}
	for _, tk := range []upbitTicker{
		ticker("KRW-BTC", 1, 900e9), ticker("KRW-ETH", 1, 300e9), ticker("KRW-SOL", 1, 100e9), ticker("KRW-XRP", 1, 200e9),
		ticker("KRW-DOGE", 2, 80e9),  // 거래대금 1위(고정 제외)
		ticker("KRW-ADA", -1, 70e9),  // 거래대금 2위
		ticker("KRW-PUMP", 30, 5e9),  // 상승률 1위
		ticker("KRW-DUMP", -20, 3e9), // 하락률 1위
		ticker("KRW-WARN", 0, 2e9),   // 유의 지정
		ticker("KRW-TINY", 90, 1e8),  // 거래대금이 너무 작아 제외
		ticker("KRW-LINK", 0.5, 60e9),
	} {
		snap.Tickers[tk.Market] = tk
	}
	snap.Warning["KRW-WARN"] = true

	var got []string
	for _, tg := range selectInsightTargets(snap) {
		got = append(got, tg.Symbol+":"+tg.Reason)
	}
	want := []string{
		"KRW-BTC:주요 코인", "KRW-ETH:주요 코인", "KRW-SOL:주요 코인", "KRW-XRP:주요 코인",
		"KRW-DOGE:거래대금 상위", "KRW-ADA:거래대금 상위", "KRW-PUMP:상승률 상위", "KRW-DUMP:하락률 상위", "KRW-WARN:유의·주의 지정",
	}
	if strings.Join(got, ",") != strings.Join(want, ",") {
		t.Errorf("selectInsightTargets() =\n%v\nwant\n%v", got, want)
	}
}

func TestBuildCoinDataAndMood(t *testing.T) {
	snap := marketSnapshot{
		Tickers: map[string]upbitTicker{"KRW-SOL": {Market: "KRW-SOL", TradePrice: 241300, SignedChangeRate: -0.04821, AccTradePrice24h: 312e9}},
		Names:   map[string]string{"KRW-SOL": "솔라나"}, Warning: map[string]bool{}, Caution: map[string]bool{"KRW-SOL": true},
	}
	candles := []upbitDayCandle{{TradePrice: 241300, AccTradePrice: 100e9}}
	for i := 0; i < 7; i++ {
		candles = append(candles, upbitDayCandle{TradePrice: 230000, AccTradePrice: 156e9}) // 7일 평균 1,560억 → 2.0배
	}

	d := buildCoinData(insightTarget{Symbol: "KRW-SOL", Reason: "주요 코인"}, snap, candles)

	if d.Change != -4.82 || d.VolumeRatio != 2.0 || d.Change7d != 4.91 || d.Mood != "약간 약세" {
		t.Errorf("buildCoinData() = %+v", d)
	}
	prompt := buildCoinPrompt(d, nil, nil, time.Date(2026, 11, 4, 0, 0, 0, 0, time.UTC))
	for _, want := range []string{"현재가: 241,300원", "등락률(전일 종가 대비): -4.82%", "3,120억원 (최근 7일 평균의 2.0배)",
		"7일 등락률: +4.91%", "유의/주의 지정: 투자 주의", "분위기 라벨(규칙으로 정함): 약간 약세", "<news>관련 기사 없음</news>"} {
		if !strings.Contains(prompt, want) {
			t.Errorf("prompt에 %q 없음:\n%s", want, prompt)
		}
	}
	if moodScore(-4.82) != 26 || moodScore(30) != 100 {
		t.Errorf("moodScore() = %d, %d", moodScore(-4.82), moodScore(30))
	}
}

func TestValidateInsight(t *testing.T) {
	prompt := "등락률: -4.82%\n7일 등락률: +4.91%\n"
	ids := map[string]bool{"c1-1": true}
	ok := insightOutput{Summary: "솔라나는 -4.82% 내렸습니다.", Drivers: []string{"7일 기준으로는 +4.91%입니다."}, Sources: []string{"c1-1"}}
	if err := validateInsight(ok, prompt, ids); err != nil {
		t.Errorf("정상 응답을 거절함: %v", err)
	}

	cases := map[string]insightOutput{
		"없는 기사":  {Summary: "요약", Drivers: []string{"a"}, Sources: []string{"c9-9"}},
		"없는 수치":  {Summary: "솔라나는 7.5% 내렸습니다.", Drivers: []string{"a"}},
		"매수 권유":  {Summary: "지금 매수하세요.", Drivers: []string{"a"}},
		"가격 예측":  {Summary: "곧 오를 것입니다.", Drivers: []string{"a"}},
		"빈 요약":   {Summary: " ", Drivers: []string{"a"}},
		"근거 없음":  {Summary: "요약"},
		"부분 일치":  {Summary: "1~2% 내렸습니다.", Drivers: []string{"a"}}, // 입력의 -4.82%에 '2%'가 들어 있어도 거절
		"말로 풀어씀": {Summary: "마이너스 4.82퍼센트 내렸습니다.", Drivers: []string{"a"}},
	}
	for name, out := range cases {
		if validateInsight(out, prompt, ids) == nil {
			t.Errorf("%s: 거절해야 함", name)
		}
	}
}

func TestLoadInsightPrompt(t *testing.T) {
	p, err := loadInsightPrompt("")
	if err != nil || p.Version != DefaultInsightPromptVersion || p.CoinSystem == "" || p.MarketSystem == "" || p.Schema["type"] != "OBJECT" || p.Temperature != 0.3 {
		t.Fatalf("기본 버전 로드 실패: %+v, %v", p.Version, err)
	}
	if strings.HasSuffix(p.CoinSystem, "\n") {
		t.Error("파일 끝 줄바꿈은 떼어야 함 (요청이 바뀌지 않도록)")
	}
	// 지난 버전도 비교용으로 남아 있다
	if v2, err := loadInsightPrompt("daily-insight-v2"); err != nil || v2.CoinSystem == p.CoinSystem {
		t.Errorf("v2 로드 실패 또는 v3와 같음: %v", err)
	}
	if _, err := loadInsightPrompt("no-such-version"); err == nil {
		t.Error("없는 버전은 오류여야 함")
	}
}

func TestCheckWordConsistency(t *testing.T) {
	prompt := "상승 0종 / 하락 6종 / 보합 0종\n등락률: -2.84%\n7일 등락률: +4.91%\n24시간 거래대금: 100억원\n"

	ok := []string{
		"상장 코인 중 상승 0종, 하락 6종으로 모두 내렸습니다.",
		"전일 대비 -2.84% 하락했고, 7일 기준으로는 4.91% 상승했습니다.",
		"2.84%의 하락세를 보였습니다.",
		"거래대금은 100억원으로 감소했습니다.",       // 부호 없는 값은 판단하지 않음
		"-2.84% 상승률 상위라는 말은 지표 이름이다.", // '률'이 붙으면 방향이 아님
	}
	for _, text := range ok {
		if err := checkWordConsistency(text, prompt); err != nil {
			t.Errorf("%q: 정상인데 거절함: %v", text, err)
		}
	}

	bad := []string{
		"하락 0종, 하락 6종으로 전부 내림세였습니다.", // 실제 사례
		"상승 종목 6종을 기록했습니다.",
		"전일 대비 2.84% 상승했습니다.",
		"7일 기준 4.91% 하락했습니다.",
		"+2.84%를 기록했습니다만 -2.84% 상승은 아닙니다.",
		"전일 대비 +2.84% 변동했습니다. 그리고 +2.84% 하락",
	}
	for _, text := range bad {
		if checkWordConsistency(text, prompt) == nil {
			t.Errorf("%q: 거절해야 함", text)
		}
	}
}

func TestNormalizeNumbers(t *testing.T) {
	got := normalizeNumbers("중앙값 마이너스 1.78퍼센트, 비트코인 플러스 2퍼센트, 이더리움 3.5 퍼센트, 도지 마이너스 9.65%")
	if got != "중앙값 -1.78%, 비트코인 +2%, 이더리움 3.5%, 도지 -9.65%" {
		t.Errorf("normalizeNumbers() = %q", got)
	}
}

func TestCryptoContextFilter(t *testing.T) {
	if containsAny("디자인 스튜디오 소폰이 공모전 수상작을 공개했다", cryptoContext) {
		t.Error("가상자산과 관계없는 기사를 걸러야 함")
	}
	if !containsAny("소폰(SOPH) 업비트 유의 종목 지정", cryptoContext) {
		t.Error("가상자산 기사는 남겨야 함")
	}
}

func TestCleanNewsAndCited(t *testing.T) {
	if got := cleanNewsText("<b>솔라나</b> &quot;급락&quot; &amp; 회복"); got != `솔라나 "급락" & 회복` {
		t.Errorf("cleanNewsText() = %q", got)
	}
	news := []NewsItem{{ID: "c1-1", Title: "a"}, {ID: "c1-2", Title: "b"}}
	got := citedNews(news, []string{"c1-2", "c1-2", "c9-9"})
	if len(got) != 1 || got[0].ID != "c1-2" {
		t.Errorf("citedNews() = %+v", got)
	}
}

func TestFormatting(t *testing.T) {
	if formatWon(102990000) != "102,990,000원" || formatWon(3.36) != "3.36원" || formatEok(312e9) != "3,120억원" || formatEok(1e7) != "1억원 미만" {
		t.Errorf("formatting: %s %s %s %s", formatWon(102990000), formatWon(3.36), formatEok(312e9), formatEok(1e7))
	}
}

func TestCheckBackground(t *testing.T) {
	withContext := "<data>…</data>\n<context>\n[k1-1] 2026-10-25 | 소송 종결 | …\n</context>\n"
	ok := insightOutput{Background: "지난 10월 25일에는 SEC 소송이 종결되며 거래량이 늘었습니다.", Sources: []string{"k1-1"}}
	if err := checkBackground(ok, withContext); err != nil {
		t.Errorf("정상 배경을 거절함: %v", err)
	}
	if err := checkBackground(insightOutput{}, "<data>…</data>"); err != nil {
		t.Errorf("배경이 비어 있으면 통과해야 함: %v", err)
	}
	bad := map[string]struct {
		out    insightOutput
		prompt string
	}{
		"배경 기사 없는데 씀":  {insightOutput{Background: "지난주 호재가 있었습니다.", Sources: []string{"k1-1"}}, "<data>…</data>"},
		"인과 표현":        {insightOutput{Background: "지난 소송 종결 때문에 오늘 하락했습니다.", Sources: []string{"k1-1"}}, withContext},
		"배경 기사 인용 안 함": {insightOutput{Background: "지난 10월 25일 소송이 종결됐습니다."}, withContext},
	}
	for name, c := range bad {
		if checkBackground(c.out, c.prompt) == nil {
			t.Errorf("%s: 거절해야 함", name)
		}
	}
}

func TestAttachBackgroundSources(t *testing.T) {
	prompt := "<context>\n[k1-1] 2026-10-25 10:20 | 소송 종결 | …\n[k1-2] 2026-10-30 15:05 | 송금망 | …\n</context>\n"
	out := attachBackgroundSources(insightOutput{Background: "지난 10월 25일에는 소송이 종결됐습니다."}, prompt)
	if strings.Join(out.Sources, ",") != "k1-1" {
		t.Errorf("날짜가 맞는 배경 기사만 붙여야 함: %v", out.Sources)
	}
	// 배경 기사 줄의 날짜 표기를 그대로 옮긴 경우: 화면용 표기로 바꾸고 인용도 붙인다 (평가셋에서 나온 사례)
	out = attachBackgroundSources(insightOutput{Background: "지난 2026-10-30에는 송금망이 늘었습니다."}, prompt)
	if out.Background != "지난 10월 30일에는 송금망이 늘었습니다." || strings.Join(out.Sources, ",") != "k1-2" {
		t.Errorf("ISO 날짜 정리·인용: %q %v", out.Background, out.Sources)
	}
	out = attachBackgroundSources(insightOutput{Background: "지난달 소식이 있었습니다."}, prompt)
	if len(out.Sources) != 0 || checkBackground(out, prompt) == nil {
		t.Errorf("날짜가 없으면 붙이지 않고 검증에서 거절해야 함: %v", out.Sources)
	}
}

func TestPickContext(t *testing.T) {
	today := []NewsItem{{Title: "오늘 기사", URL: "https://a.com/today"}}
	md := func(title, url string) map[string]any {
		return map[string]any{"title": title, "url": url, "published": "2026-10-28 09:00", "snippet": "요약"}
	}
	matches := []pineconeMatch{
		{Score: 0.81, Metadata: md("오늘 기사", "https://a.com/today")}, // 오늘 기사와 같음 → 제외
		{Score: 0.79, Metadata: md("과거 기사 1", "https://a.com/1")},
		{Score: 0.78, Metadata: md("과거 기사 1", "https://b.com/copy")}, // 같은 제목(다른 언론사 재전송) → 제외
		{Score: 0.72, Metadata: md("과거 기사 2", "https://a.com/2")},
		{Score: 0.50, Metadata: md("관련 약함", "https://a.com/3")}, // 유사도 기준 미달 → 제외
	}
	got := pickContext(matches, today, "k1-")
	if len(got) != 2 || got[0].ID != "k1-1" || got[0].Title != "과거 기사 1" || got[1].Title != "과거 기사 2" || got[0].PublishedAt != "2026-10-28 09:00" {
		t.Errorf("pickContext() = %+v", got)
	}
	var b strings.Builder
	writeContext(&b, got)
	if !strings.Contains(b.String(), "<context>\n[k1-1] 2026-10-28 09:00 | 과거 기사 1 | 요약\n") {
		t.Errorf("writeContext() = %q", b.String())
	}
	b.Reset()
	writeContext(&b, nil)
	if b.Len() != 0 {
		t.Error("배경 기사가 없으면 블록을 넣지 않아야 함")
	}
	var nilRetriever *contextRetriever
	if nilRetriever.related(context.Background(), "q", "KRW-BTC", nil, time.Now(), "k") != nil {
		t.Error("아카이브 설정이 없으면 배경 기사 없이 진행해야 함")
	}
}
