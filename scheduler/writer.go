package main

import (
	"Bitground-go/config"
	"Bitground-go/model"
	"Bitground-go/service"
	"Bitground-go/util"
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"time"
)

// 쓰기 단계 (Lambda B, VPC 안): DB에 쓰는 작업만 한다. 외부 API는 부르지 않는다.
// 수집 단계(Lambda A, VPC 밖)가 외부에서 가져온 값을 WriterRequest로 넘긴다.
// 로컬에서는 같은 프로세스에서 부르되 JSON으로 한 번 바꿔 Lambda와 같은 계약을 거친다 (inProcessWriter).

// WriterRequest 수집 단계 → 쓰기 단계 요청 (Lambda Invoke 페이로드)
type WriterRequest struct {
	Op       string           `json:"op"`               // prepare | apply | insights
	RunID    string           `json:"runId,omitempty"`  // 수집 단계의 실행 ID (로그를 묶어 보기 위함)
	Period   string           `json:"period,omitempty"` // 실행 기준 정시 (로그용)
	Apply    *ApplyRequest    `json:"apply,omitempty"`
	Insights *InsightsRequest `json:"insights,omitempty"`
}

const (
	opPrepare  = "prepare"
	opApply    = "apply"
	opInsights = "insights"
)

// WriterResponse 쓰기 단계 응답. Error가 있으면 일부 작업이 실패한 것 (수집 단계가 실패로 끝내 재시도된다)
type WriterResponse struct {
	SymbolMap map[string]int `json:"symbolMap,omitempty"` // prepare: 활성 코인 심볼 → ID
	Failed    []string       `json:"failed,omitempty"`    // 실패한 작업 이름
	Error     string         `json:"error,omitempty"`
}

// ApplyRequest 매시 실행의 DB 작업에 필요한 외부 값. 조회에 실패한 값은 비워 보내고, 그 값이 필요한 작업은 실패로 남는다
type ApplyRequest struct {
	Now         time.Time                       `json:"now"`                   // 실행 기준 정시
	Tickers     map[string]model.UpbitCoinPrice `json:"tickers,omitempty"`     // 업비트 원화 전 종목 시세
	Markets     []model.UpbitCoinList           `json:"markets,omitempty"`     // 업비트 마켓 목록 (매시)
	MarketIndex *service.MarketIndex            `json:"marketIndex,omitempty"` // 시장 지수
}

// InsightsRequest 생성한 AI 인사이트 저장 (0시, 생성이 오래 걸려 매시 작업과 따로 보낸다)
type InsightsRequest struct {
	Now   time.Time            `json:"now"`
	Batch service.InsightBatch `json:"batch"`
}

// handleWriter 쓰기 단계 한 번. db 연결은 호출한 쪽이 관리한다
func handleWriter(ctx context.Context, db *sql.DB, cfg config.Config, req WriterRequest) (WriterResponse, error) {
	switch req.Op {
	case opPrepare:
		symbolMap, err := service.GetActiveCoinsSymbols(ctx, db)
		if err != nil {
			return WriterResponse{}, fmt.Errorf("활성화된 코인 심볼 조회 실패: %w", err)
		}
		return WriterResponse{SymbolMap: symbolMap}, nil
	case opApply:
		if req.Apply == nil {
			return WriterResponse{}, errors.New("apply 요청 본문이 없음")
		}
		jobs := &jobRunner{}
		err := applyHourly(ctx, db, cfg, *req.Apply, jobs)
		return result(jobs, err), nil
	case opInsights:
		if req.Insights == nil {
			return WriterResponse{}, errors.New("insights 요청 본문이 없음")
		}
		jobs := &jobRunner{}
		err := jobs.run(ctx, "인사이트 저장", func(ctx context.Context) error {
			return service.SaveInsights(ctx, db, req.Insights.Batch, req.Insights.Now.In(cfg.Location))
		})
		return result(jobs, err), nil
	default:
		return WriterResponse{}, fmt.Errorf("알 수 없는 작업: %q", req.Op)
	}
}

func result(jobs *jobRunner, err error) WriterResponse {
	if err == nil {
		return WriterResponse{}
	}
	return WriterResponse{Failed: jobs.failedNames(), Error: err.Error()}
}

// applyHourly 매시 DB 작업. 같은 테이블을 순서대로 고치므로 차례로 실행한다
func applyHourly(ctx context.Context, db *sql.DB, cfg config.Config, req ApplyRequest, jobs *jobRunner) error {
	step := func(name string, fn func(ctx context.Context) error) error { return jobs.run(ctx, name, fn) }
	now := req.Now.In(cfg.Location)
	daily := util.TimeCheck(now).Coin // 매일 0시

	symbolMap, err := service.GetActiveCoinsSymbols(ctx, db)
	if err != nil {
		return fmt.Errorf("활성화된 코인 심볼 조회 실패: %w", err)
	}
	// 시즌 정산·스플릿은 DB의 시즌 일정으로 정한다 (관리자가 바꾸면 다음 정각에 반영)
	schedule, err := service.GetSeasonSchedule(ctx, db, cfg.Location)
	if err != nil {
		return fmt.Errorf("현재 시즌 일정 조회 실패: %w", err)
	}
	seasonDue, splitDue := schedule.SeasonDue(now), schedule.SplitDue(now)
	slog.InfoContext(ctx, "쓰기 시작", "base_time", now.Format(time.DateTime), "season_id", schedule.ID,
		"split_starts_at", formatTime(schedule.SplitAt), "ends_at", formatTime(schedule.EndsAt),
		"season", seasonDue, "split", splitDue, "daily", daily, "tickers", len(req.Tickers))

	// 시세가 필요한 작업: 조회에 실패해 비어 있으면 실패로 남겨 재시도에 맡긴다
	needTickers := func(fn func(ctx context.Context) error) func(ctx context.Context) error {
		return func(ctx context.Context) error {
			if len(req.Tickers) == 0 {
				return errors.New("업비트 시세가 없음 (수집 단계 조회 실패)")
			}
			return fn(ctx)
		}
	}

	var errs []error
	// 코인 목록은 매시 갱신 (새 상장·유의·주의 지정·거래대금이 한 시간 안에 화면에 반영되게)
	errs = append(errs, step("코인 업데이트", needTickers(func(ctx context.Context) error {
		if len(req.Markets) == 0 {
			return errors.New("업비트 마켓 목록이 없음 (수집 단계 조회 실패)")
		}
		return service.SaveCoins(ctx, db, req.Markets, req.Tickers)
	})))
	if daily {
		errs = append(errs, step("오래된 알림 정리", func(ctx context.Context) error {
			_, err := service.PurgeOldNotifications(ctx, db, now)
			return err
		}))
	}
	// 업비트 목록에서 사라진 코인(거래지원 종료)은 매시간 상장 폐지로 표시하고 보유분을 정리한다 (랭킹보다 먼저)
	errs = append(errs, step("상장 폐지 확인", needTickers(func(ctx context.Context) error {
		return service.ApplyDelistings(ctx, db, req.Tickers)
	})))
	if splitDue {
		errs = append(errs, step("스플릿(현금 지급)", func(ctx context.Context) error {
			return service.UpdateSplit(ctx, db, cfg, schedule.SplitAt)
		}))
	}

	// 랭킹은 매시간, 일일 자산 스냅샷은 0시에만 함께 남긴다
	coinPrices, coinPriceHistory := service.CoinPrices(symbolMap, req.Tickers)
	rankErr := step("랭킹 업데이트", needTickers(func(ctx context.Context) error {
		return service.UpdateRank(ctx, db, coinPrices, schedule.ID, daily, now)
	}))
	errs = append(errs, rankErr)
	if rankErr == nil {
		errs = append(errs, step("코인 가격 히스토리 업데이트", func(ctx context.Context) error {
			return service.UpdateCoinPriceHistory(ctx, db, coinPriceHistory, now)
		}))
	}

	if seasonDue {
		if rankErr != nil {
			// 최종 랭킹·청산 가격 없이 정산하면 티어와 자산이 틀어지므로 정산하지 않고 재시도에 맡긴다
			errs = append(errs, jobs.skip(ctx, "시즌 정산", "랭킹 계산 실패로 시즌 정산을 건너뜀"))
		} else {
			errs = append(errs, step("시즌 정산", func(ctx context.Context) error {
				return service.UpdateSeason(ctx, db, cfg, schedule, coinPrices)
			}))
		}
	}

	// 랭킹이 바뀌었으니 백엔드의 랭킹 캐시를 비운다 (시즌 정산까지 끝난 뒤. 실패해도 캐시 TTL이 받쳐 주므로 경고만)
	if rankErr == nil && cfg.BackendURL != "" {
		if err := service.NotifyRankingsRefreshed(ctx, cfg.BackendURL, cfg.SchedulerKey); err != nil {
			slog.WarnContext(ctx, "랭킹 갱신 알림 실패 (백엔드 캐시는 TTL로 만료)", "error", err)
		}
	}

	// 이전 실행에서 정산은 됐지만 백엔드에 알리지 못한 기록 (예약 주문 정리·사용자 알림이 빠지지 않게)
	errs = append(errs, step("백엔드 알림 재시도", func(ctx context.Context) error {
		return service.RetryPendingNotifications(ctx, db, cfg.BackendURL, cfg.SchedulerKey)
	}))

	if req.MarketIndex != nil {
		errs = append(errs, step("마켓 인덱스 저장", func(ctx context.Context) error {
			return service.SaveMarketIndex(ctx, db, *req.MarketIndex, now)
		}))
	}
	return errors.Join(errs...)
}

// Writer 수집 단계가 쓰기 단계를 부르는 방법 (로컬: 같은 프로세스, 운영: Lambda Invoke)
type Writer interface {
	Call(ctx context.Context, req WriterRequest) (WriterResponse, error)
}

// inProcessWriter 같은 프로세스에서 쓰기 단계를 부른다. 요청·응답을 JSON으로 한 번 바꿔 Lambda와 같은 계약을 거친다
type inProcessWriter struct {
	db  *sql.DB
	cfg config.Config
}

func (w inProcessWriter) Call(ctx context.Context, req WriterRequest) (WriterResponse, error) {
	var decoded WriterRequest
	if err := roundTrip(req, &decoded); err != nil {
		return WriterResponse{}, fmt.Errorf("쓰기 요청 직렬화 실패: %w", err)
	}
	resp, err := handleWriter(ctx, w.db, w.cfg, decoded)
	if err != nil {
		return WriterResponse{}, err
	}
	var out WriterResponse
	if err := roundTrip(resp, &out); err != nil {
		return WriterResponse{}, fmt.Errorf("쓰기 응답 직렬화 실패: %w", err)
	}
	return out, nil
}

func roundTrip(in, out any) error {
	b, err := json.Marshal(in)
	if err != nil {
		return err
	}
	return json.Unmarshal(b, out)
}
