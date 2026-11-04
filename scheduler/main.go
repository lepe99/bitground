package main

import (
	"Bitground-go/config"
	"Bitground-go/model"
	"Bitground-go/service"
	"Bitground-go/util"
	"context"
	"database/sql"
	"errors"
	"fmt"
	"log"
	"os"
	"sync"
	"time"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/joho/godotenv"
)

// Result Lambda 응답
type Result struct {
	Message string `json:"message"`
}

// main AWS Lambda에서는 핸들러를 등록하고, 로컬에서는 .env를 읽어 한 번 실행합니다.
// RUN_HOURLY=1이면 로컬에서 Lambda + EventBridge(매시 정각)를 흉내 내 계속 실행합니다.
func main() {
	if os.Getenv("AWS_LAMBDA_RUNTIME_API") != "" {
		lambda.Start(handler)
		return
	}

	if err := godotenv.Load(); err != nil {
		log.Printf(".env 파일을 읽지 못해 환경변수만 사용합니다: %v", err)
	}
	if os.Getenv("RUN_HOURLY") == "1" {
		runHourly()
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Minute)
	defer cancel()

	if _, err := handler(ctx); err != nil {
		os.Exit(1)
	}
}

// runHourly 로컬 테스트용 매시 실행: 시작할 때 한 번(그 시각의 정시 기준), 이후 매시 정각 10초 뒤마다.
// 한 번 실패해도 멈추지 않고 다음 정각에 다시 실행한다 (Lambda 재시도 대신)
func runHourly() {
	log.Println("매시 실행 모드 시작 (RUN_HOURLY=1)")
	at := time.Now()
	for {
		ctx, cancel := context.WithTimeout(context.Background(), 15*time.Minute)
		if _, err := handleAt(ctx, at); err != nil {
			log.Println("이번 실행 실패, 다음 정각에 다시 실행:", err)
		}
		cancel()

		next := time.Now().Truncate(time.Hour).Add(time.Hour)
		log.Printf("다음 실행: %s\n", next.Format(time.DateTime))
		time.Sleep(time.Until(next.Add(10 * time.Second)))
		at = next
	}
}

// handler 한 시간마다 EventBridge Scheduler가 호출합니다.
// 에러를 반환하면 Lambda가 재시도하므로, 재실행돼도 결과가 같도록 각 작업을 멱등하게 만든다.
func handler(ctx context.Context) (Result, error) {
	return handleAt(ctx, time.Time{})
}

// handleAt at이 비어 있으면 설정의 기준 시각(cfg.Now), 있으면 그 시각이 속한 정시를 기준으로 실행한다.
// (매시 실행 모드의 첫 실행은 14:42에 시작해도 15시가 아니라 14시 작업을 해야 하므로 반올림하지 않고 내린다)
func handleAt(ctx context.Context, at time.Time) (Result, error) {
	cfg, err := config.Load()
	if err != nil {
		log.Println("설정 오류:", err)
		return Result{}, err
	}
	// 시즌·일일 작업과 날짜 계산은 서비스 기준 시간대(KST)로 한다. Lambda 기본값은 UTC
	time.Local = cfg.Location

	now, err := cfg.Now()
	if err != nil {
		log.Println("설정 오류:", err)
		return Result{}, err
	}
	if !at.IsZero() {
		now = at.In(cfg.Location).Truncate(time.Hour)
	}

	db, err := config.ConnectDB(ctx, cfg.DB)
	if err != nil {
		log.Println("데이터베이스 연결 실패:", err)
		return Result{}, err
	}
	defer func(db *sql.DB) {
		if err := db.Close(); err != nil {
			log.Printf("데이터베이스 연결 종료 실패: %v\n", err)
		}
	}(db)

	if err := run(ctx, db, cfg, now); err != nil {
		log.Println("업데이트 작업 중 일부가 실패했습니다:", err)
		return Result{}, err
	}

	msg := "모든 업데이트 작업이 성공적으로 완료되었습니다."
	log.Println(msg)
	return Result{Message: msg}, nil
}

func run(ctx context.Context, db *sql.DB, cfg config.Config, now time.Time) error {
	flags := util.TimeCheck(now, cfg.Mode)
	if cfg.InsightEveryHour && !flags.Insight {
		// 로컬 테스트용: 0시까지 기다리지 않고 매번 인사이트를 다시 만든다 (같은 날짜 행을 덮어씀)
		flags.Insight = true
	}
	log.Printf("기준 시각: %s, 업데이트 플래그: %+v\n", now.Format(time.DateTime), flags)

	// 이후 작업이 모두 필요로 하는 값. 실패하면 더 진행하지 않는다
	symbolMap, err := service.GetActiveCoinsSymbols(ctx, db)
	if err != nil {
		return fmt.Errorf("활성화된 코인 심볼 조회 실패: %w", err)
	}
	seasonID, err := service.GetCurrentSeasonID(ctx, db)
	if err != nil {
		return fmt.Errorf("현재 시즌 ID 조회 실패: %w", err)
	}

	// 서로 독립적인 외부 API 작업은 병렬로 돌리고, 각자 자기 에러 변수에만 쓴다
	var marketErr, insightErr, newsErr error
	var wg sync.WaitGroup
	wg.Go(func() {
		marketErr = step("마켓 인덱스 업데이트", func() error {
			return service.UpdateMarketIndex(ctx, db)
		})
	})
	newsCfg := service.NewsArchiveConfig{
		GeminiAPIKey:      cfg.GeminiAPIKey,
		EmbedModel:        cfg.NewsEmbedModel,
		EmbedDimension:    cfg.NewsEmbedDimension,
		NaverClientID:     cfg.NaverClientID,
		NaverClientSecret: cfg.NaverClientSecret,
		PineconeAPIKey:    cfg.PineconeAPIKey,
		PineconeIndex:     cfg.PineconeIndex,
		EmbedPerMinute:    cfg.NewsEmbedPerMinute,
	}
	if newsCfg.Enabled() {
		// 매시간 새 기사를 쌓고, 0시에는 보관 기간이 지난 기사도 지운다
		wg.Go(func() {
			newsErr = step("뉴스 아카이브", func() error {
				return service.ArchiveNews(ctx, newsCfg, symbolMap, now, flags.Coin)
			})
		})
	} else {
		log.Println("뉴스 아카이브 생략: Gemini·네이버·Pinecone 키 중 없는 것이 있음")
	}
	switch {
	case !flags.Insight:
		log.Println("인사이트 업데이트 생략")
	case cfg.GeminiAPIKey == "":
		log.Println("인사이트 업데이트 생략: GEMINI_API_KEY 없음")
	default:
		wg.Go(func() {
			insightErr = step("인사이트 업데이트", func() error {
				return service.UpdateInsight(ctx, db, service.InsightConfig{
					GeminiAPIKey:      cfg.GeminiAPIKey,
					GeminiModel:       cfg.GeminiModel,
					NaverClientID:     cfg.NaverClientID,
					NaverClientSecret: cfg.NaverClientSecret,
					PromptVersion:     cfg.InsightPromptVersion,
					PineconeAPIKey:    cfg.PineconeAPIKey,
					PineconeIndex:     cfg.PineconeIndex,
					EmbedModel:        cfg.NewsEmbedModel,
					EmbedDimension:    cfg.NewsEmbedDimension,
				}, symbolMap, now)
			})
		})
	}

	// 아래 작업은 같은 테이블을 순서대로 고치므로 차례로 실행한다
	var errs []error
	if flags.Coin {
		errs = append(errs, step("코인 업데이트", func() error {
			return service.UpdateCoins(ctx, db)
		}))
	}
	if flags.Split {
		errs = append(errs, step("스플릿(현금 지급)", func() error {
			return service.UpdateSplit(ctx, db, cfg, now)
		}))
	}

	// 랭킹은 매시간, 일일 자산 스냅샷은 0시에만 함께 남긴다
	var coinPrices map[int]float64
	var coinPriceHistory map[int]model.UpbitCoinPrice
	rankErr := step("랭킹 업데이트", func() error {
		var err error
		coinPrices, coinPriceHistory, err = service.UpdateRank(ctx, db, symbolMap, seasonID, flags.Coin, now)
		return err
	})
	errs = append(errs, rankErr)
	if rankErr == nil {
		errs = append(errs, step("코인 가격 히스토리 업데이트", func() error {
			return service.UpdateCoinPriceHistory(ctx, db, coinPriceHistory)
		}))
	}

	if flags.Season {
		if rankErr != nil {
			// 최종 랭킹·청산 가격 없이 정산하면 티어와 자산이 틀어지므로 정산하지 않고 재시도에 맡긴다
			errs = append(errs, errors.New("랭킹 계산 실패로 시즌 정산을 건너뜀"))
		} else {
			errs = append(errs, step("시즌 정산", func() error {
				return service.UpdateSeason(ctx, db, cfg, now, seasonID, coinPrices)
			}))
		}
	}

	wg.Wait()
	errs = append(errs, marketErr, insightErr, newsErr)
	return errors.Join(errs...)
}

// step 작업 하나를 실행하고 시작·완료·실패를 기록합니다.
func step(name string, fn func() error) error {
	log.Printf("%s 시작\n", name)
	if err := fn(); err != nil {
		log.Printf("%s 실패: %v\n", name, err)
		return fmt.Errorf("%s: %w", name, err)
	}
	log.Printf("%s 완료\n", name)
	return nil
}
