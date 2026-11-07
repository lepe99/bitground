package main

import (
	"Bitground-go/applog"
	"Bitground-go/config"
	"Bitground-go/model"
	"Bitground-go/service"
	"Bitground-go/util"
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"log/slog"
	"os"
	"strconv"
	"sync"
	"time"

	"github.com/aws/aws-lambda-go/lambda"
	"github.com/aws/aws-lambda-go/lambdacontext"
	"github.com/joho/godotenv"
)

// Result Lambda 응답
type Result struct {
	Message string `json:"message"`
}

// main AWS Lambda에서는 핸들러를 등록하고, 로컬에서는 .env를 읽어 한 번 실행합니다.
// RUN_HOURLY=1이면 로컬에서 Lambda + EventBridge(매시 정각)를 흉내 내 계속 실행합니다.
func main() {
	applog.SetupFromEnv()
	if os.Getenv("AWS_LAMBDA_RUNTIME_API") != "" {
		lambda.Start(handler)
		return
	}

	if err := godotenv.Load(); err == nil {
		applog.SetupFromEnv() // .env의 LOG_FORMAT·LOG_LEVEL 반영
	} else {
		slog.Info(".env 파일 없음, 환경변수만 사용", "error", err)
	}
	if os.Getenv("RUN_HOURLY") == "1" {
		runHourly()
		return
	}
	if os.Getenv("NEWS_REEMBED") != "" {
		reembedNews()
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Minute)
	defer cancel()

	if _, err := handler(ctx); err != nil {
		os.Exit(1)
	}
}

// hourlyRetries 한 정시 실행이 실패했을 때 같은 정시로 다시 시도하는 횟수와 간격 (Lambda 재시도 대신)
const (
	hourlyRetries    = 3
	hourlyRetryDelay = time.Minute
)

// runHourly 로컬 테스트용 매시 실행: 시작할 때 한 번(그 시각의 정시 기준), 이후 매시 정각 10초 뒤마다.
// 실패하면 1분 간격으로 같은 정시 작업을 세 번까지 다시 시도하고, 그래도 안 되면 다음 정각을 기다린다.
// (Docker가 다시 시작될 때 DB보다 먼저 떠서 첫 실행이 실패하면 그 시각의 스플릿·정산을 통째로 놓쳤다.
// 작업은 멱등하고 정산·지급은 scheduler_runs로 한 번만 실행되므로 다시 시도해도 안전하다)
func runHourly() {
	slog.Info("매시 실행 모드 시작 (RUN_HOURLY=1)")
	at := time.Now()
	for {
		for attempt := 0; ; attempt++ {
			ctx, cancel := context.WithTimeout(context.Background(), 15*time.Minute)
			_, err := handleAt(ctx, at) // 실패 내용은 handleAt이 기록한다
			cancel()
			if err == nil || attempt == hourlyRetries {
				break
			}
			slog.Warn("실행 실패, 같은 정시로 다시 시도", "attempt", attempt+1, "max_retries", hourlyRetries, "delay", hourlyRetryDelay.String())
			time.Sleep(hourlyRetryDelay)
		}

		next := time.Now().Truncate(time.Hour).Add(time.Hour)
		slog.Info("다음 실행 대기", "next", next.In(applog.KST).Format(time.DateTime))
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
	ctx = applog.With(ctx, slog.String("run_id", runID(ctx)))
	cfg, err := config.Load()
	if err != nil {
		slog.ErrorContext(ctx, "설정 오류", "error", err)
		return Result{}, err
	}
	// 시즌·일일 작업과 날짜 계산은 서비스 기준 시간대(KST)로 한다. Lambda 기본값은 UTC
	time.Local = cfg.Location

	now, err := cfg.Now()
	if err != nil {
		slog.ErrorContext(ctx, "설정 오류", "error", err)
		return Result{}, err
	}
	if !at.IsZero() {
		now = at.In(cfg.Location).Truncate(time.Hour)
	}
	ctx = applog.With(ctx, slog.String("period", now.Format("2006-01-02T15")))

	db, err := config.ConnectDB(ctx, cfg.DB)
	if err != nil {
		slog.ErrorContext(ctx, "데이터베이스 연결 실패", "error", err)
		return Result{}, err
	}
	defer func(db *sql.DB) {
		if err := db.Close(); err != nil {
			slog.WarnContext(ctx, "데이터베이스 연결 종료 실패", "error", err)
		}
	}(db)

	started := time.Now()
	jobs := &jobRunner{}
	if err := run(ctx, db, cfg, now, jobs); err != nil {
		// 작업별 에러는 각 작업이 이미 남겼으므로 여기서는 실패한 작업 이름만 모아 한 줄로 남긴다
		slog.ErrorContext(ctx, "실행 실패", "failed_jobs", jobs.failedNames(), "duration_ms", time.Since(started).Milliseconds())
		return Result{}, err
	}

	msg := "모든 업데이트 작업이 성공적으로 완료되었습니다."
	slog.InfoContext(ctx, "실행 완료", "duration_ms", time.Since(started).Milliseconds())
	return Result{Message: msg}, nil
}

// runID Lambda에서는 요청 ID(CloudWatch의 START/END 줄과 같은 값), 로컬에서는 임의 값
func runID(ctx context.Context) string {
	if lc, ok := lambdacontext.FromContext(ctx); ok {
		return lc.AwsRequestID
	}
	b := make([]byte, 6)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

func run(ctx context.Context, db *sql.DB, cfg config.Config, now time.Time, jobs *jobRunner) error {
	step := func(name string, fn func(ctx context.Context) error) error { return jobs.run(ctx, name, fn) }
	flags := util.TimeCheck(now)
	if cfg.InsightEveryHour && !flags.Insight {
		// 로컬 테스트용: 0시까지 기다리지 않고 매번 인사이트를 다시 만든다 (같은 날짜 행을 덮어씀)
		flags.Insight = true
	}

	// 이후 작업이 모두 필요로 하는 값. 실패하면 더 진행하지 않는다
	symbolMap, err := service.GetActiveCoinsSymbols(ctx, db)
	if err != nil {
		return fmt.Errorf("활성화된 코인 심볼 조회 실패: %w", err)
	}
	// 시즌 정산·스플릿은 DB의 시즌 일정으로 정한다 (관리자가 바꾸면 다음 정각에 반영)
	schedule, err := service.GetSeasonSchedule(ctx, db, cfg.Location)
	if err != nil {
		return fmt.Errorf("현재 시즌 일정 조회 실패: %w", err)
	}
	seasonID := schedule.ID
	flags.Season = schedule.SeasonDue(now)
	flags.Split = schedule.SplitDue(now)
	slog.InfoContext(ctx, "실행 시작", "base_time", now.Format(time.DateTime), "season_id", seasonID,
		"split_starts_at", formatTime(schedule.SplitAt), "ends_at", formatTime(schedule.EndsAt),
		"season", flags.Season, "split", flags.Split, "coin", flags.Coin, "insight", flags.Insight)

	// 서로 독립적인 외부 API 작업은 병렬로 돌리고, 각자 자기 에러 변수에만 쓴다
	var marketErr, insightErr, newsErr error
	var wg sync.WaitGroup
	wg.Go(func() {
		marketErr = step("마켓 인덱스 업데이트", func(ctx context.Context) error {
			return service.UpdateMarketIndex(ctx, db, cfg.CoinGeckoAPIKey)
		})
	})
	newsCfg := newsArchiveConfig(cfg)
	if newsCfg.Enabled() {
		// 매시간 새 기사를 쌓고, 0시에는 보관 기간이 지난 기사도 지운다
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
			insightErr = step("인사이트 업데이트", func(ctx context.Context) error {
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
		errs = append(errs, step("코인 업데이트", func(ctx context.Context) error {
			return service.UpdateCoins(ctx, db)
		}))
	}
	if flags.Coin { // 매일 0시
		errs = append(errs, step("오래된 알림 정리", func(ctx context.Context) error {
			_, err := service.PurgeOldNotifications(ctx, db, now)
			return err
		}))
	}
	// 업비트 목록에서 사라진 코인(거래지원 종료)은 매시간 상장 폐지로 표시하고 보유분을 정리한다 (랭킹보다 먼저)
	errs = append(errs, step("상장 폐지 확인", func(ctx context.Context) error {
		return service.SyncDelistings(ctx, db)
	}))
	if flags.Split {
		errs = append(errs, step("스플릿(현금 지급)", func(ctx context.Context) error {
			return service.UpdateSplit(ctx, db, cfg, schedule.SplitAt)
		}))
	}

	// 랭킹은 매시간, 일일 자산 스냅샷은 0시에만 함께 남긴다
	var coinPrices map[int]float64
	var coinPriceHistory map[int]model.UpbitCoinPrice
	rankErr := step("랭킹 업데이트", func(ctx context.Context) error {
		var err error
		coinPrices, coinPriceHistory, err = service.UpdateRank(ctx, db, symbolMap, seasonID, flags.Coin, now)
		return err
	})
	errs = append(errs, rankErr)
	if rankErr == nil {
		errs = append(errs, step("코인 가격 히스토리 업데이트", func(ctx context.Context) error {
			return service.UpdateCoinPriceHistory(ctx, db, coinPriceHistory)
		}))
	}

	if flags.Season {
		if rankErr != nil {
			// 최종 랭킹·청산 가격 없이 정산하면 티어와 자산이 틀어지므로 정산하지 않고 재시도에 맡긴다
			errs = append(errs, jobs.skip(ctx, "시즌 정산", "랭킹 계산 실패로 시즌 정산을 건너뜀"))
		} else {
			errs = append(errs, step("시즌 정산", func(ctx context.Context) error {
				return service.UpdateSeason(ctx, db, cfg, schedule, coinPrices)
			}))
		}
	}

	// 이전 실행에서 정산은 됐지만 백엔드에 알리지 못한 기록 (예약 주문 정리·사용자 알림이 빠지지 않게)
	errs = append(errs, step("백엔드 알림 재시도", func(ctx context.Context) error {
		return service.RetryPendingNotifications(ctx, db, cfg.BackendURL, cfg.SchedulerKey)
	}))

	wg.Wait()
	errs = append(errs, marketErr, insightErr, newsErr)
	return errors.Join(errs...)
}

func formatTime(t time.Time) string {
	if t.IsZero() {
		return ""
	}
	return t.Format(time.DateTime)
}

// jobRunner 작업을 실행하며 시작·완료·실패와 소요 시간을 남기고, 실패한 작업 이름을 모은다 (작업은 병렬로도 실행됨)
type jobRunner struct {
	mu     sync.Mutex
	failed []string
}

// run 작업 하나를 실행한다. fn이 받는 context로 남긴 로그에는 job 이름이 붙는다
func (j *jobRunner) run(ctx context.Context, name string, fn func(ctx context.Context) error) error {
	ctx = applog.With(ctx, slog.String("job", name))
	started := time.Now()
	slog.InfoContext(ctx, "작업 시작")
	if err := fn(ctx); err != nil {
		slog.ErrorContext(ctx, "작업 실패", "error", err, "duration_ms", time.Since(started).Milliseconds())
		j.addFailed(name)
		return fmt.Errorf("%s: %w", name, err)
	}
	slog.InfoContext(ctx, "작업 완료", "duration_ms", time.Since(started).Milliseconds())
	return nil
}

// skip 앞 작업 실패로 실행하지 않은 작업도 실패로 남긴다 (재시도 대상)
func (j *jobRunner) skip(ctx context.Context, name, reason string) error {
	slog.ErrorContext(applog.With(ctx, slog.String("job", name)), "작업 건너뜀", "reason", reason)
	j.addFailed(name)
	return errors.New(reason)
}

func (j *jobRunner) addFailed(name string) {
	j.mu.Lock()
	defer j.mu.Unlock()
	j.failed = append(j.failed, name)
}

func (j *jobRunner) failedNames() []string {
	j.mu.Lock()
	defer j.mu.Unlock()
	return append([]string(nil), j.failed...)
}

func newsArchiveConfig(cfg config.Config) service.NewsArchiveConfig {
	return service.NewsArchiveConfig{
		GeminiAPIKey:      cfg.GeminiAPIKey,
		EmbedModel:        cfg.NewsEmbedModel,
		EmbedDimension:    cfg.NewsEmbedDimension,
		NaverClientID:     cfg.NaverClientID,
		NaverClientSecret: cfg.NaverClientSecret,
		PineconeAPIKey:    cfg.PineconeAPIKey,
		PineconeIndex:     cfg.PineconeIndex,
		EmbedPerMinute:    cfg.NewsEmbedPerMinute,
	}
}

// reembedNews 뉴스 아카이브 전체를 지금 임베딩 형식으로 다시 임베딩한다 (한 번씩 손으로 실행).
// NEWS_REEMBED=all이면 전체, 숫자면 그 개수만 (먼저 몇 건으로 확인할 때)
//
//	docker compose --profile app --profile scheduler run --rm -e RUN_HOURLY= -e NEWS_REEMBED=all scheduler
func reembedNews() {
	ctx := applog.With(context.Background(), slog.String("run_id", runID(context.Background())), slog.String("job", "뉴스 다시 임베딩"))
	cfg, err := config.Load()
	if err != nil {
		slog.ErrorContext(ctx, "설정 오류", "error", err)
		os.Exit(1)
	}
	limit, _ := strconv.Atoi(os.Getenv("NEWS_REEMBED")) // all이면 0 → 전체
	started := time.Now()
	done, failed, err := service.ReembedNews(ctx, newsArchiveConfig(cfg), limit)
	if err != nil {
		slog.ErrorContext(ctx, "다시 임베딩 실패", "done", done, "failed", failed, "error", err)
		os.Exit(1)
	}
	slog.InfoContext(ctx, "다시 임베딩 완료", "done", done, "failed", failed, "duration_ms", time.Since(started).Milliseconds())
}
