package service

import (
	"Bitground-go/applog"
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"strings"
	"sync"
	"time"
)

// 일일 AI 인사이트 (매일 0시). 순서:
//  1. 대상 선정·데이터 수집 (코드): 업비트 현재가·일봉·유의/주의 지정, 네이버 뉴스
//  2. 분위기 라벨 (코드): 등락률 규칙
//  3. 해설 생성 (모델): 코인마다 따로, 받은 자료만 근거로
//  4. 검증 (코드): 형식, 인용한 기사 id, 퍼센트 수치, 투자 권유 표현
//
// (기존: 데이터 없이 '어제 시장을 분석할 수 있다고 가정'하게 해 한 번에 8개를 쓰게 했고, 추가 코인도 모델이 추정해 골랐음)

const marketOverallSymbol = "MARKET_OVERALL"

// 동시에 보내는 Gemini 요청 수 (요청 한도 여유)
const insightConcurrency = 3

// InsightConfig 인사이트 생성에 필요한 외부 API 설정. 네이버 키가 없으면 뉴스 없이 시세만으로 쓴다
type InsightConfig struct {
	GeminiAPIKey  string
	GeminiModel   string
	PromptVersion string // 비우면 DefaultInsightPromptVersion
	// 배경 자료(뉴스 아카이브) 검색. 비어 있으면 배경 자료 없이 쓴다
	PineconeAPIKey    string
	PineconeIndex     string
	EmbedModel        string
	EmbedDimension    int
	NaverClientID     string
	NaverClientSecret string
}

// Insight 저장할 인사이트 한 건
type Insight struct {
	Symbol  string
	Mood    string
	Score   int
	Summary string
	Drivers []string
	Risks   []string
	Sources []NewsItem // 모델이 실제로 인용한 기사만 (오늘 기사 + 배경 기사)
	// Background 최근 30일 배경 (뉴스 아카이브 기사로, 오늘 움직임과 인과로 잇지 않음)
	Background string
	Reason     string // 분석 대상으로 고른 이유 (시장 전체는 빈 값)
}

type insightJob struct {
	symbol, system, prompt, mood, reason string
	score                                int
	news                                 []NewsItem // 오늘 기사
	related                              []NewsItem // 최근 30일 배경 기사 (뉴스 아카이브)
}

// InsightBatch 생성한 인사이트 묶음 (수집 단계에서 만들어 저장 단계로 넘긴다)
type InsightBatch struct {
	Insights      []Insight `json:"insights"`
	Failed        []string  `json:"failed,omitempty"` // 생성에 실패한 심볼 (어제 행을 남겨 둔다)
	Model         string    `json:"model"`
	PromptVersion string    `json:"promptVersion"`
}

// BuildInsights 시세·뉴스를 모아 Gemini로 인사이트를 만든다 (DB를 쓰지 않음, 수집 단계)
func BuildInsights(ctx context.Context, ic InsightConfig, symbolMap map[string]int, now time.Time) (InsightBatch, error) {
	prompt, err := loadInsightPrompt(ic.PromptVersion)
	if err != nil {
		return InsightBatch{}, err
	}
	snap, err := collectMarketSnapshot(ctx, symbolMap)
	if err != nil {
		return InsightBatch{}, err
	}
	targets := selectInsightTargets(snap)
	slog.InfoContext(ctx, "인사이트 대상 선정", "count", len(targets), "targets", targetSymbols(targets))

	var coins []coinData
	for _, t := range targets {
		candles, err := fetchDayCandles(ctx, t.Symbol)
		if err != nil {
			slog.WarnContext(ctx, "일봉 조회 실패, 7일 지표 없이 진행", "symbol", t.Symbol, "error", err)
		}
		coins = append(coins, buildCoinData(t, snap, candles))
		time.Sleep(120 * time.Millisecond) // 업비트 시세 API 초당 요청 제한 여유
	}
	market := buildMarketData(snap)
	retriever := newContextRetriever(ctx, ic)

	marketNews := collectNews(ctx, ic, "가상자산", []string{"가상자산", "비트코인", "코인", "암호화폐"}, now, "m")
	marketRelated := retriever.related(ctx, contextQuery("가상자산 시장 전반", marketNews), "", marketNews, now, "km")
	jobs := []insightJob{{
		symbol: marketOverallSymbol, system: prompt.MarketSystem, mood: market.Mood, score: moodScore(market.MedianChange),
		news: marketNews, related: marketRelated, prompt: buildMarketPrompt(market, marketNews, marketRelated, now),
	}}
	for i, d := range coins {
		query, must := newsKeywords(d)
		news := collectNews(ctx, ic, query, must, now, fmt.Sprintf("c%d-", i+1))
		related := retriever.related(ctx, contextQuery(d.Name+"("+strings.TrimPrefix(d.Symbol, "KRW-")+")", news), d.Symbol, news, now, fmt.Sprintf("k%d-", i+1))
		jobs = append(jobs, insightJob{
			symbol: d.Symbol, system: prompt.CoinSystem, prompt: buildCoinPrompt(d, news, related, now),
			mood: d.Mood, score: moodScore(d.Change), reason: d.Reason, news: news, related: related,
		})
	}
	related := 0
	for _, j := range jobs {
		related += len(j.related)
	}
	slog.InfoContext(ctx, "인사이트 배경 기사 검색", "related", related)

	results := make([]*Insight, len(jobs))
	sem := make(chan struct{}, insightConcurrency)
	var wg sync.WaitGroup
	for i, job := range jobs {
		wg.Go(func() {
			sem <- struct{}{}
			defer func() { <-sem }()
			sources := append(append([]NewsItem{}, job.news...), job.related...)
			ids := map[string]bool{}
			for _, n := range sources {
				ids[n.ID] = true
			}
			ctx := applog.With(ctx, slog.String("symbol", job.symbol)) // 이 항목의 Gemini 호출·재시도 로그에 심볼을 붙인다
			out, err := generateInsightText(ctx, ic.GeminiAPIKey, ic.GeminiModel, prompt, job.system, job.prompt, ids)
			if err != nil {
				slog.WarnContext(ctx, "인사이트 생성 실패, 이 항목은 건너뜀", "error", err)
				return
			}
			results[i] = &Insight{
				Symbol: job.symbol, Mood: job.mood, Score: job.score, Reason: job.reason,
				Summary: out.Summary, Drivers: out.Drivers, Risks: out.Risks, Sources: citedNews(sources, out.Sources),
				Background: strings.TrimSpace(out.Background),
			}
		})
	}
	wg.Wait()

	var insights []Insight
	var failed []string
	for i, r := range results {
		if r != nil {
			insights = append(insights, *r)
		} else {
			failed = append(failed, jobs[i].symbol)
		}
	}
	if len(insights) == 0 {
		return InsightBatch{}, errors.New("인사이트를 하나도 만들지 못했습니다")
	}
	slog.InfoContext(ctx, "인사이트 생성", "created", len(insights), "total", len(jobs))
	return InsightBatch{Insights: insights, Failed: failed, Model: ic.GeminiModel, PromptVersion: prompt.Version}, nil
}

// SaveInsights 생성한 인사이트를 now 날짜로 저장한다 (저장 단계)
func SaveInsights(ctx context.Context, db *sql.DB, b InsightBatch, now time.Time) error {
	return insertInsights(ctx, db, b.Insights, b.Failed, b.Model, b.PromptVersion, now)
}

// collectNews 네이버 키가 없거나 검색이 실패하면 뉴스 없이 진행한다 (시세만으로도 쓸 수 있음)
func collectNews(ctx context.Context, ic InsightConfig, query string, must []string, now time.Time, idPrefix string) []NewsItem {
	if ic.NaverClientID == "" || ic.NaverClientSecret == "" {
		return nil
	}
	items, err := fetchNews(ctx, ic.NaverClientID, ic.NaverClientSecret, query, must, now, idPrefix)
	if err != nil {
		slog.WarnContext(ctx, "뉴스 검색 실패, 뉴스 없이 진행", "query", query, "error", err)
		return nil
	}
	return items
}

// citedNews 모델이 인용한 id의 기사만 순서대로
func citedNews(news []NewsItem, ids []string) []NewsItem {
	var out []NewsItem
	seen := map[string]bool{}
	for _, id := range ids {
		for _, n := range news {
			if n.ID == id && !seen[id] {
				out = append(out, n)
				seen[id] = true
			}
		}
	}
	return out
}

// insertInsights 그날 행을 지우고 선정 순서대로 다시 넣는다. 화면은 id 순서로 버튼을 놓으므로 재실행해도 순서가 맞고,
// 오늘 대상에서 빠진 코인의 이전 실행 결과도 남지 않는다. 생성에 실패한 대상(keep)은 이전 결과를 그대로 둔다
func insertInsights(ctx context.Context, db *sql.DB, insights []Insight, keep []string, model, promptVersion string, now time.Time) error {
	queryCtx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()

	date := now.Format(time.DateOnly)
	return withTx(queryCtx, db, func(tx *sql.Tx) error {
		del := "DELETE FROM ai_insights WHERE date = ?"
		args := []any{date}
		if len(keep) > 0 {
			del += " AND symbol NOT IN (?" + strings.Repeat(", ?", len(keep)-1) + ")"
			for _, s := range keep {
				args = append(args, s)
			}
		}
		if _, err := tx.ExecContext(queryCtx, del, args...); err != nil {
			return fmt.Errorf("오늘 인사이트 정리 에러: %w", err)
		}

		stmt, err := tx.PrepareContext(queryCtx, `
			INSERT INTO ai_insights (symbol, insight, score, date, mood, drivers, risks, sources, background, pick_reason, prompt_version, model)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE
				insight = VALUES(insight), score = VALUES(score), mood = VALUES(mood),
				drivers = VALUES(drivers), risks = VALUES(risks), sources = VALUES(sources), background = VALUES(background),
				pick_reason = VALUES(pick_reason), prompt_version = VALUES(prompt_version), model = VALUES(model)`)
		if err != nil {
			return fmt.Errorf("쿼리 준비 에러: %w", err)
		}
		defer stmt.Close()

		for _, in := range insights {
			drivers, _ := json.Marshal(nonNil(in.Drivers))
			risks, _ := json.Marshal(nonNil(in.Risks))
			sources, _ := json.Marshal(in.Sources)
			if in.Sources == nil {
				sources = []byte("[]")
			}
			if _, err := stmt.ExecContext(queryCtx, in.Symbol, in.Summary, in.Score, date, in.Mood,
				string(drivers), string(risks), string(sources), nullable(in.Background), nullable(in.Reason), promptVersion, model); err != nil {
				return fmt.Errorf("데이터베이스 삽입 에러(%s): %w", in.Symbol, err)
			}
		}
		return nil
	})
}

func nonNil(s []string) []string {
	if s == nil {
		return []string{}
	}
	return s
}

func nullable(s string) any {
	if s == "" {
		return nil
	}
	return s
}
