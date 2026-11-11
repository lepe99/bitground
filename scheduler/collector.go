package main

import (
	"Bitground-go/config"
	"Bitground-go/service"
	"Bitground-go/util"
	"context"
	"errors"
	"fmt"
	"log/slog"
	"sync"
	"time"
)

// 수집 단계 (Lambda A, VPC 밖): 외부 API(업비트·CoinGecko·네이버·Gemini·Pinecone)만 부르고 DB에는 직접 접근하지 않는다.
// DB가 필요한 일은 모두 쓰기 단계(Writer)에 맡긴다. DB 접속 정보 없이 외부 API 키만 가진다.

// collect 한 시간분 실행. 오래 걸리는 인사이트 생성(0시)은 매시 작업과 따로 진행해 랭킹 갱신이 기다리지 않게 한다
func collect(ctx context.Context, cfg config.Config, now time.Time, w Writer, jobs *jobRunner) error {
	step := func(name string, fn func(ctx context.Context) error) error { return jobs.run(ctx, name, fn) }
	flags := util.TimeCheck(now)
	if cfg.InsightEveryHour && !flags.Insight {
		// 로컬 테스트용: 0시까지 기다리지 않고 매번 인사이트를 다시 만든다 (같은 날짜 행을 덮어씀)
		flags.Insight = true
	}

	// 이후 작업이 모두 필요로 하는 값. 실패하면 더 진행하지 않는다
	prep, err := w.Call(ctx, WriterRequest{Op: opPrepare})
	if err != nil {
		return fmt.Errorf("쓰기 단계 준비 실패: %w", err)
	}
	symbolMap := prep.SymbolMap
	slog.InfoContext(ctx, "실행 시작", "base_time", now.Format(time.DateTime), "coins", len(symbolMap),
		"daily", flags.Coin, "insight", flags.Insight)

	// DB와 무관하게 오래 걸리는 작업은 따로 진행한다
	var newsErr, insightErr error
	var wg sync.WaitGroup
	newsCfg := newsArchiveConfig(cfg)
	if newsCfg.Enabled() {
		// 매시간 새 기사를 쌓고, 0시에는 보관 기간이 지난 기사도 지운다 (뉴스 아카이브는 Pinecone에만 쓴다)
		wg.Go(func() {
			newsErr = step("뉴스 아카이브", func(ctx context.Context) error {
				return service.ArchiveNews(ctx, newsCfg, symbolMap, now, flags.Coin)
			})
		})
	} else {
		slog.InfoContext(ctx, "뉴스 아카이브 생략: Gemini·네이버·Pinecone 키 중 없는 것이 있음")
	}
	switch {
	case !flags.Insight:
		slog.DebugContext(ctx, "인사이트 업데이트 생략 (0시 아님)")
	case cfg.GeminiAPIKey == "":
		slog.WarnContext(ctx, "인사이트 업데이트 생략: GEMINI_API_KEY 없음")
	default:
		wg.Go(func() {
			var batch service.InsightBatch
			insightErr = step("인사이트 생성", func(ctx context.Context) error {
				var err error
				batch, err = service.BuildInsights(ctx, insightConfig(cfg), symbolMap, now)
				return err
			})
			if insightErr == nil {
				insightErr = callWriter(ctx, w, WriterRequest{Op: opInsights, Insights: &InsightsRequest{Now: now, Batch: batch}})
			}
		})
	}

	// 매시 DB 작업에 필요한 외부 값을 동시에 가져온다. 조회 실패는 각자 실패로 남기고, 그 값이 필요한 쓰기만 실패한다
	req := ApplyRequest{Now: now}
	var fetchErrs [3]error
	var fetch sync.WaitGroup
	fetch.Go(func() {
		fetchErrs[0] = step("업비트 시세 조회", func(ctx context.Context) error {
			var err error
			req.Tickers, err = service.FetchTickers(ctx)
			return err
		})
	})
	// 코인 목록(이름·유의·주의·거래대금)은 매시 갱신한다. 화면이 업비트 REST 대신 이 목록을 쓰므로 (백엔드 /coins/markets)
	fetch.Go(func() {
		fetchErrs[1] = step("업비트 마켓 목록 조회", func(ctx context.Context) error {
			var err error
			req.Markets, err = service.FetchMarkets(ctx)
			return err
		})
	})
	fetch.Go(func() {
		fetchErrs[2] = step("마켓 인덱스 조회", func(ctx context.Context) error {
			idx, err := service.FetchMarketIndex(ctx, cfg.CoinGeckoAPIKey)
			if err == nil {
				req.MarketIndex = &idx
			}
			return err
		})
	})
	fetch.Wait()
	applyErr := callWriter(ctx, w, WriterRequest{Op: opApply, Apply: &req})

	wg.Wait()
	return errors.Join(fetchErrs[0], fetchErrs[1], fetchErrs[2], applyErr, insightErr, newsErr)
}

// callWriter 쓰기 단계를 부르고, 일부 작업이 실패했으면 에러로 돌려준다 (수집 단계가 실패로 끝나 재시도된다)
func callWriter(ctx context.Context, w Writer, req WriterRequest) error {
	resp, err := w.Call(ctx, req)
	if err != nil {
		return fmt.Errorf("쓰기 단계(%s) 호출 실패: %w", req.Op, err)
	}
	if resp.Error != "" {
		return fmt.Errorf("쓰기 단계(%s) 실패 %v: %s", req.Op, resp.Failed, resp.Error)
	}
	return nil
}

func insightConfig(cfg config.Config) service.InsightConfig {
	return service.InsightConfig{
		GeminiAPIKey:      cfg.GeminiAPIKey,
		GeminiModel:       cfg.GeminiModel,
		NaverClientID:     cfg.NaverClientID,
		NaverClientSecret: cfg.NaverClientSecret,
		PromptVersion:     cfg.InsightPromptVersion,
		PineconeAPIKey:    cfg.PineconeAPIKey,
		PineconeIndex:     cfg.PineconeIndex,
		EmbedModel:        cfg.NewsEmbedModel,
		EmbedDimension:    cfg.NewsEmbedDimension,
	}
}
