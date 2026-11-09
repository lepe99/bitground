package main

import (
	"Bitground-go/service"
	"context"
	"encoding/json"
	"reflect"
	"strings"
	"testing"
	"time"
)

// 수집 단계(A) ↔ 쓰기 단계(B) 계약. Lambda Invoke는 JSON으로 오가므로 값이 그대로 전달되는지,
// 한쪽 조회가 실패했을 때 어떤 작업이 멈추고 어떤 작업은 계속되는지 확인한다

func TestInsightBatchSurvivesJSON(t *testing.T) {
	batch := service.InsightBatch{
		Insights: []service.Insight{{
			Symbol: "KRW-BTC", Mood: "상승", Score: 72, Summary: "요약", Drivers: []string{"근거"}, Risks: []string{"위험"},
			Sources:    []service.NewsItem{{ID: "c1-1", Title: "기사", URL: "https://news.example/1", PublishedAt: "2026-11-10"}},
			Background: "배경", Reason: "거래대금 상위",
		}},
		Failed: []string{"KRW-ETH"}, Model: "gemini-test", PromptVersion: "v3",
	}
	var got service.InsightBatch
	if err := roundTrip(InsightsRequest{Now: kst("2026-11-11 00:00:00"), Batch: batch}, &struct {
		Batch *service.InsightBatch `json:"batch"`
	}{&got}); err != nil {
		t.Fatal(err)
	}
	if !reflect.DeepEqual(got, batch) {
		t.Errorf("JSON을 거친 뒤 달라짐\n got %+v\nwant %+v", got, batch)
	}
}

func TestInsightsAreSavedByWriter(t *testing.T) {
	e := setup(t)
	w := inProcessWriter{db: testDB.DB, cfg: e.cfg}
	batch := service.InsightBatch{Model: "gemini-test", PromptVersion: "v3", Insights: []service.Insight{
		{Symbol: "MARKET_OVERALL", Mood: "보합", Score: 50, Summary: "시장", Drivers: []string{"a"}, Risks: []string{"b"}},
		{Symbol: "KRW-BTC", Mood: "상승", Score: 70, Summary: "비트코인", Drivers: []string{"a"}, Risks: []string{"b"}, Reason: "거래대금 상위"},
	}}
	if err := callWriter(context.Background(), w, WriterRequest{Op: opInsights,
		Insights: &InsightsRequest{Now: kst("2026-11-11 00:00:00"), Batch: batch}}); err != nil {
		t.Fatal(err)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM ai_insights WHERE date = '2026-11-11'`); n != 2 {
		t.Errorf("저장된 인사이트 %d건", n)
	}
}

func TestTickerOutageStopsOnlyPriceDependentJobs(t *testing.T) {
	e := setup(t)
	season := e.f.Season("26년 3시즌", kst("2026-11-01 00:00:00"), kst("2026-11-08 00:00:00"), kst("2026-11-16 00:00:00"))
	users := e.rankingFixture(season)
	e.market.FailTickers(true)

	err := e.run(t, kst("2026-11-08 00:00:00")) // 스플릿 시각 + 0시
	if err == nil {
		t.Fatal("시세 조회가 실패했는데 실행이 성공으로 끝남 (재시도 대상이어야 함)")
	}
	for _, want := range []string{"업비트 시세 조회", "상장 폐지 확인", "랭킹 업데이트", "코인 업데이트"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("실패 목록에 %q 없음: %v", want, err)
		}
	}
	// 시세가 필요 없는 작업은 그대로 한다: 스플릿 지급과 백엔드 알림, 시장 지수
	if v := e.f.Int(`SELECT cash FROM users WHERE id = ?`, users[1]); v != 22_000_000 {
		t.Errorf("스플릿 지급 후 현금 %d", v)
	}
	if got := e.backend.Received(); got != "split" {
		t.Errorf("백엔드 알림 %q", got)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM market_indices`); n != 1 {
		t.Errorf("시장 지수 %d행", n)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM user_rankings`); n != 0 {
		t.Errorf("시세 없이 랭킹이 계산됨: %d행", n)
	}

	// 업비트가 돌아오면 다음 실행에서 랭킹이 계산된다 (스플릿은 다시 지급하지 않음)
	e.market.FailTickers(false)
	if err := e.run(t, kst("2026-11-08 01:00:00")); err != nil {
		t.Fatal(err)
	}
	if n := e.f.Int(`SELECT COUNT(*) FROM user_rankings`); n != 7 {
		t.Errorf("복구 후 랭킹 %d행", n)
	}
	if v := e.f.Int(`SELECT cash FROM users WHERE id = ?`, users[1]); v != 22_000_000 {
		t.Errorf("스플릿이 다시 지급됨: %d", v)
	}
}

func TestSeasonNotSettledWithoutPrices(t *testing.T) {
	e := setup(t)
	season := e.f.Season("26년 3시즌", kst("2026-11-01 00:00:00"), time.Time{}, kst("2026-11-16 00:00:00"))
	e.rankingFixture(season)
	e.market.FailTickers(true)

	if err := e.run(t, kst("2026-11-16 00:00:00")); err == nil || !strings.Contains(err.Error(), "시즌 정산") {
		t.Fatalf("청산 가격 없이 정산하지 않고 실패로 남겨야 함: %v", err)
	}
	if got := e.f.String(`SELECT status FROM seasons WHERE id = ?`, season); got != "PENDING" {
		t.Errorf("시세 없이 시즌이 정산됨: %s", got)
	}
}

func TestWriterRejectsUnknownOp(t *testing.T) {
	e := setup(t)
	if _, err := handleWriter(context.Background(), testDB.DB, e.cfg, WriterRequest{Op: "drop"}); err == nil {
		t.Error("모르는 작업을 받아들임")
	}
	if _, err := handleWriter(context.Background(), testDB.DB, e.cfg, WriterRequest{Op: opApply}); err == nil {
		t.Error("본문 없는 apply를 받아들임")
	}
}

// 요청 JSON 모양 (Lambda 콘솔에서 B를 직접 시험할 때 쓰는 형식)
func TestWriterRequestShape(t *testing.T) {
	b, err := json.Marshal(WriterRequest{Op: opPrepare, RunID: "r1", Period: "2026-11-10T14"})
	if err != nil {
		t.Fatal(err)
	}
	if got := string(b); got != `{"op":"prepare","runId":"r1","period":"2026-11-10T14"}` {
		t.Errorf("요청 JSON %s", got)
	}
}
