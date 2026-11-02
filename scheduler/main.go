package main

import (
	"Bitground-go/config"
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
func main() {
	if os.Getenv("AWS_LAMBDA_RUNTIME_API") != "" {
		lambda.Start(handler)
		return
	}

	if err := godotenv.Load(); err != nil {
		log.Printf(".env 파일을 읽지 못해 환경변수만 사용합니다: %v", err)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Minute)
	defer cancel()

	if _, err := handler(ctx); err != nil {
		os.Exit(1)
	}
}

// handler 한 시간마다 EventBridge Scheduler가 호출합니다.
// 에러를 반환하면 Lambda가 재시도하므로, 재실행돼도 결과가 같도록 각 작업을 멱등하게 만든다.
func handler(ctx context.Context) (Result, error) {
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
	var marketErr, insightErr error
	var wg sync.WaitGroup
	wg.Go(func() {
		marketErr = step("마켓 인덱스 업데이트", func() error {
			return service.UpdateMarketIndex(ctx, db)
		})
	})
	switch {
	case !flags.Insight:
		log.Println("인사이트 업데이트 생략")
	case cfg.GeminiAPIKey == "":
		log.Println("인사이트 업데이트 생략: GOOGLE_API_KEY 없음")
	default:
		wg.Go(func() {
			insightErr = step("인사이트 업데이트", func() error {
				return service.UpdateInsight(ctx, db, cfg.GeminiAPIKey, cfg.GeminiModel, symbolMap)
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

	var coinPrices map[int]float64
	rankErr := step("랭킹 업데이트", func() error {
		err, coinPriceHistory, prices := service.UpdateRank(ctx, db, symbolMap, seasonID, flags.Coin)
		if err != nil {
			return err
		}
		coinPrices = prices
		errs = append(errs, step("코인 가격 히스토리 업데이트", func() error {
			return service.UpdateCoinPriceHistory(ctx, db, coinPriceHistory)
		}))
		return nil
	})
	errs = append(errs, rankErr)

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
	errs = append(errs, marketErr, insightErr)
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
