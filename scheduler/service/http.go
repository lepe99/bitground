package service

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// httpClient 모든 외부 호출이 공유하는 클라이언트 (연결 재사용)
// 요청별 제한 시간은 각 호출의 context로 따로 건다. 여기 값은 상한선이다.
var httpClient = &http.Client{Timeout: 4 * time.Minute}

// getJSON GET 요청을 보내고 2xx 응답 본문을 out에 디코딩합니다.
func getJSON(ctx context.Context, url string, timeout time.Duration, out any) error {
	return getJSONWithHeaders(ctx, url, timeout, nil, out)
}

// getJSONWithHeaders 인증 헤더 등이 필요한 GET 요청 (예: 네이버 뉴스 검색)
func getJSONWithHeaders(ctx context.Context, url string, timeout time.Duration, headers map[string]string, out any) error {
	reqCtx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()

	req, err := http.NewRequestWithContext(reqCtx, http.MethodGet, url, nil)
	if err != nil {
		return fmt.Errorf("HTTP 요청 생성 에러: %w", err)
	}
	req.Header.Set("Accept", "application/json")
	for k, v := range headers {
		req.Header.Set(k, v)
	}

	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("API 요청 에러: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 512))
		return fmt.Errorf("API 응답 에러: 상태 코드 %d, 응답: %s", resp.StatusCode, body)
	}

	if err := json.NewDecoder(resp.Body).Decode(out); err != nil {
		return fmt.Errorf("JSON 디코딩 에러: %w", err)
	}
	return nil
}
