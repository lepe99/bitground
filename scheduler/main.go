package main

import (
	"Bitground-go/applog"
	"Bitground-go/config"
	"Bitground-go/service"
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
		// 운영: 수집 단계(A)는 EventBridge가 매시 호출, 쓰기 단계(B)는 A가 호출한다 (SCHEDULER_ROLE)
		if os.Getenv("SCHEDULER_ROLE") == config.RoleWriter {
			lambda.Start(writerHandler)
		} else {
			lambda.Start(handler)
		}
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
	runID := newRunID(ctx)
	ctx = applog.With(ctx, slog.String("run_id", runID))
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
	period := now.Format("2006-01-02T15")
	ctx = applog.With(ctx, slog.String("period", period))

	w, closeWriter, err := newWriter(ctx, cfg, runID, period)
	if err != nil {
		slog.ErrorContext(ctx, "쓰기 단계 준비 실패", "error", err)
		return Result{}, err
	}
	defer closeWriter()

	started := time.Now()
	jobs := &jobRunner{}
	if err := collect(ctx, cfg, now, w, jobs); err != nil {
		// 작업별 에러는 각 작업이 이미 남겼으므로 여기서는 실패한 작업 이름만 모아 한 줄로 남긴다
		slog.ErrorContext(ctx, "실행 실패", "failed_jobs", jobs.failedNames(), "duration_ms", time.Since(started).Milliseconds())
		return Result{}, err
	}

	msg := "모든 업데이트 작업이 성공적으로 완료되었습니다."
	slog.InfoContext(ctx, "실행 완료", "duration_ms", time.Since(started).Milliseconds())
	return Result{Message: msg}, nil
}

// newRunID Lambda에서는 요청 ID(CloudWatch의 START/END 줄과 같은 값), 로컬에서는 임의 값
func newRunID(ctx context.Context) string {
	if lc, ok := lambdacontext.FromContext(ctx); ok {
		return lc.AwsRequestID
	}
	b := make([]byte, 6)
	_, _ = rand.Read(b)
	return hex.EncodeToString(b)
}

// run 한 프로세스에서 수집·쓰기를 모두 실행한다 (로컬, 테스트). 쓰기 단계는 JSON 계약을 거친다
func run(ctx context.Context, db *sql.DB, cfg config.Config, now time.Time, jobs *jobRunner) error {
	return collect(ctx, cfg, now, inProcessWriter{db: db, cfg: cfg}, jobs)
}

// newWriter 역할에 맞는 쓰기 단계: 한 프로세스면 DB에 직접 연결, 수집 단계(A)면 쓰기 Lambda(B) 호출
func newWriter(ctx context.Context, cfg config.Config, runID, period string) (Writer, func(), error) {
	switch cfg.Role {
	case config.RoleCollector:
		w, err := newLambdaWriter(ctx, cfg.WriterFunction)
		if err != nil {
			return nil, nil, err
		}
		return taggedWriter{inner: w, runID: runID, period: period}, func() {}, nil
	case config.RoleAll:
		db, err := config.ConnectDB(ctx, cfg.DB)
		if err != nil {
			return nil, nil, fmt.Errorf("데이터베이스 연결 실패: %w", err)
		}
		closeDB := func() {
			if err := db.Close(); err != nil {
				slog.WarnContext(ctx, "데이터베이스 연결 종료 실패", "error", err)
			}
		}
		return inProcessWriter{db: db, cfg: cfg}, closeDB, nil
	default:
		return nil, nil, fmt.Errorf("%s 역할은 매시 실행을 시작하지 않음 (수집 단계가 호출함)", cfg.Role)
	}
}

// taggedWriter 쓰기 Lambda 요청에 수집 단계의 run_id·period를 붙인다 (두 Lambda 로그를 같은 실행으로 묶어 보기 위함)
type taggedWriter struct {
	inner         Writer
	runID, period string
}

func (w taggedWriter) Call(ctx context.Context, req WriterRequest) (WriterResponse, error) {
	req.RunID, req.Period = w.runID, w.period
	return w.inner.Call(ctx, req)
}

// writerHandler 쓰기 Lambda(B) 핸들러. 호출마다 DB에 연결하고 닫는다
func writerHandler(ctx context.Context, req WriterRequest) (WriterResponse, error) {
	if req.RunID != "" {
		ctx = applog.With(ctx, slog.String("run_id", req.RunID), slog.String("period", req.Period))
	}
	ctx = applog.With(ctx, slog.String("op", req.Op))
	cfg, err := config.Load()
	if err != nil {
		slog.ErrorContext(ctx, "설정 오류", "error", err)
		return WriterResponse{}, err
	}
	time.Local = cfg.Location
	db, err := config.ConnectDB(ctx, cfg.DB)
	if err != nil {
		slog.ErrorContext(ctx, "데이터베이스 연결 실패", "error", err)
		return WriterResponse{}, err
	}
	defer db.Close()
	resp, err := handleWriter(ctx, db, cfg, req)
	if err != nil {
		slog.ErrorContext(ctx, "쓰기 실패", "error", err)
	} else if resp.Error != "" {
		slog.ErrorContext(ctx, "쓰기 일부 실패", "failed_jobs", resp.Failed)
	}
	return resp, err
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
	ctx := applog.With(context.Background(), slog.String("run_id", newRunID(context.Background())), slog.String("job", "뉴스 다시 임베딩"))
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
