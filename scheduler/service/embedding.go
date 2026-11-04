package service

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"net/url"
	"time"
)

// Gemini 임베딩 (gemini-embedding-2). 기본 3072차원이지만 outputDimensionality로 줄여 저장 공간을 아낀다

// 임베딩 용도: 저장하는 문서 / 검색어 (같은 모델이라도 용도를 알려 주면 검색 품질이 좋아진다)
const (
	embedTaskDocument = "RETRIEVAL_DOCUMENT"
	embedTaskQuery    = "RETRIEVAL_QUERY"
)

func embedText(ctx context.Context, apiKey, model string, dimension int, taskType, text string) ([]float32, error) {
	body, err := json.Marshal(map[string]any{
		"content":              map[string]any{"parts": []map[string]string{{"text": text}}},
		"taskType":             taskType,
		"outputDimensionality": dimension,
	})
	if err != nil {
		return nil, err
	}
	apiURL := "https://generativelanguage.googleapis.com/v1beta/models/" + url.PathEscape(model) + ":embedContent"

	started := time.Now()
	for attempt := 0; ; attempt++ {
		last := attempt >= len(geminiRetryBackoff)
		status, respBody, err := postJSON(ctx, apiURL, apiKey, body)
		if err == nil && status == http.StatusOK {
			// 한 번 실행에 100번 가까이 불러 기본(INFO)에서는 남기지 않는다 (LOG_LEVEL=debug)
			slog.DebugContext(ctx, "임베딩 호출", "model", model, "task", taskType, "attempts", attempt+1,
				"latency_ms", time.Since(started).Milliseconds())
			var res struct {
				Embedding struct {
					Values []float32 `json:"values"`
				} `json:"embedding"`
			}
			if err := json.Unmarshal(respBody, &res); err != nil {
				return nil, fmt.Errorf("임베딩 응답 해석 실패: %w", err)
			}
			if len(res.Embedding.Values) != dimension {
				return nil, fmt.Errorf("임베딩 차원이 %d가 아니라 %d입니다", dimension, len(res.Embedding.Values))
			}
			return res.Embedding.Values, nil
		}
		if err == nil && status != http.StatusTooManyRequests && status < 500 {
			return nil, fmt.Errorf("임베딩 실패: 상태 코드 %d, 응답: %.300s", status, respBody)
		}
		if last {
			if err != nil {
				return nil, fmt.Errorf("임베딩 실패: %w", err)
			}
			return nil, fmt.Errorf("임베딩 실패: 상태 코드 %d", status)
		}
		slog.WarnContext(ctx, "임베딩 일시적 실패, 재시도", "status", status, "error", err, "backoff", geminiRetryBackoff[attempt].String(), "attempt", attempt+1)
		select {
		case <-time.After(geminiRetryBackoff[attempt]):
		case <-ctx.Done():
			return nil, ctx.Err()
		}
	}
}
