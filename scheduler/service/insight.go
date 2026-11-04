package service

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"log"
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
	GeminiAPIKey      string
	GeminiModel       string
	PromptVersion     string // 비우면 DefaultInsightPromptVersion
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
	Sources []NewsItem // 모델이 실제로 인용한 기사만
	Reason  string     // 분석 대상으로 고른 이유 (시장 전체는 빈 값)
}

type insightJob struct {
	symbol, system, prompt, mood, reason string
	score                                int
	news                                 []NewsItem
}

func UpdateInsight(ctx context.Context, db *sql.DB, ic InsightConfig, symbolMap map[string]int, now time.Time) error {
	prompt, err := loadInsightPrompt(ic.PromptVersion)
	if err != nil {
		return err
	}
	snap, err := collectMarketSnapshot(ctx, symbolMap)
	if err != nil {
		return err
	}
	targets := selectInsightTargets(snap)
	log.Printf("인사이트 대상 %d종: %v\n", len(targets), targets)

	var coins []coinData
	for _, t := range targets {
		candles, err := fetchDayCandles(ctx, t.Symbol)
		if err != nil {
			log.Printf("일봉 조회 실패(%s), 7일 지표 없이 진행: %v\n", t.Symbol, err)
		}
		coins = append(coins, buildCoinData(t, snap, candles))
		time.Sleep(120 * time.Millisecond) // 업비트 시세 API 초당 요청 제한 여유
	}
	market := buildMarketData(snap)

	jobs := []insightJob{{
		symbol: marketOverallSymbol, system: prompt.MarketSystem, mood: market.Mood, score: moodScore(market.MedianChange),
		news: collectNews(ctx, ic, "가상자산", []string{"가상자산", "비트코인", "코인", "암호화폐"}, now, "m"),
	}}
	jobs[0].prompt = buildMarketPrompt(market, jobs[0].news, now)
	for i, d := range coins {
		query, must := newsKeywords(d)
		news := collectNews(ctx, ic, query, must, now, fmt.Sprintf("c%d-", i+1))
		jobs = append(jobs, insightJob{
			symbol: d.Symbol, system: prompt.CoinSystem, prompt: buildCoinPrompt(d, news, now),
			mood: d.Mood, score: moodScore(d.Change), reason: d.Reason, news: news,
		})
	}

	results := make([]*Insight, len(jobs))
	sem := make(chan struct{}, insightConcurrency)
	var wg sync.WaitGroup
	for i, job := range jobs {
		wg.Go(func() {
			sem <- struct{}{}
			defer func() { <-sem }()
			ids := map[string]bool{}
			for _, n := range job.news {
				ids[n.ID] = true
			}
			out, err := generateInsightText(ctx, ic.GeminiAPIKey, ic.GeminiModel, prompt, job.system, job.prompt, ids)
			if err != nil {
				log.Printf("인사이트 생성 실패(%s), 이 항목은 건너뜀: %v\n", job.symbol, err)
				return
			}
			results[i] = &Insight{
				Symbol: job.symbol, Mood: job.mood, Score: job.score, Reason: job.reason,
				Summary: out.Summary, Drivers: out.Drivers, Risks: out.Risks, Sources: citedNews(job.news, out.Sources),
			}
		})
	}
	wg.Wait()

	var insights []Insight
	for _, r := range results {
		if r != nil {
			insights = append(insights, *r)
		}
	}
	if len(insights) == 0 {
		return errors.New("인사이트를 하나도 만들지 못했습니다")
	}
	log.Printf("인사이트 %d/%d건 생성\n", len(insights), len(jobs))
	return insertInsights(ctx, db, insights, ic.GeminiModel, prompt.Version, now)
}

// collectNews 네이버 키가 없거나 검색이 실패하면 뉴스 없이 진행한다 (시세만으로도 쓸 수 있음)
func collectNews(ctx context.Context, ic InsightConfig, query string, must []string, now time.Time, idPrefix string) []NewsItem {
	if ic.NaverClientID == "" || ic.NaverClientSecret == "" {
		return nil
	}
	items, err := fetchNews(ctx, ic.NaverClientID, ic.NaverClientSecret, query, must, now, idPrefix)
	if err != nil {
		log.Println(err)
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

// insertInsights 같은 날짜·심볼이면 덮어쓴다 (재실행해도 결과가 하나)
func insertInsights(ctx context.Context, db *sql.DB, insights []Insight, model, promptVersion string, now time.Time) error {
	queryCtx, cancel := context.WithTimeout(ctx, 20*time.Second)
	defer cancel()

	return withTx(queryCtx, db, func(tx *sql.Tx) error {
		stmt, err := tx.PrepareContext(queryCtx, `
			INSERT INTO ai_insights (symbol, insight, score, date, mood, drivers, risks, sources, pick_reason, prompt_version, model)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			ON DUPLICATE KEY UPDATE
				insight = VALUES(insight), score = VALUES(score), mood = VALUES(mood),
				drivers = VALUES(drivers), risks = VALUES(risks), sources = VALUES(sources),
				pick_reason = VALUES(pick_reason), prompt_version = VALUES(prompt_version), model = VALUES(model)`)
		if err != nil {
			return fmt.Errorf("쿼리 준비 에러: %w", err)
		}
		defer stmt.Close()

		date := now.Format(time.DateOnly)
		for _, in := range insights {
			drivers, _ := json.Marshal(nonNil(in.Drivers))
			risks, _ := json.Marshal(nonNil(in.Risks))
			sources, _ := json.Marshal(in.Sources)
			if in.Sources == nil {
				sources = []byte("[]")
			}
			if _, err := stmt.ExecContext(queryCtx, in.Symbol, in.Summary, in.Score, date, in.Mood,
				string(drivers), string(risks), string(sources), nullable(in.Reason), promptVersion, model); err != nil {
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
