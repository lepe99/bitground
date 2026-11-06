package service

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"time"
)

// Pinecone REST 클라이언트 (뉴스 아카이브용으로 필요한 것만: 인덱스 확인, fetch, upsert, list, delete)
// https://docs.pinecone.io/reference/api

// 인덱스 조회(제어) 주소. 테스트에서 가짜 서버로 바꾼다
var pineconeControlURL = "https://api.pinecone.io"

const (
	// 분기마다 새 안정 버전이 나오고 각 버전은 최소 12개월 지원된다 (https://docs.pinecone.io/reference/api/versioning)
	pineconeAPIVersion = "2026-07"
	// upsert·delete 한 번에 보내는 최대 개수 (API 제한 100 이하)
	pineconeBatch = 100
	// fetch는 id를 쿼리 문자열로 보내므로 URL이 너무 길어지지 않게 나눈다
	pineconeFetchBatch = 50
)

type pineconeClient struct {
	apiKey string
	host   string // 데이터 요청을 보낼 인덱스 주소 (https://… 없이)
}

type pineconeVector struct {
	ID       string         `json:"id"`
	Values   []float32      `json:"values"`
	Metadata map[string]any `json:"metadata,omitempty"`
}

// newPineconeClient 인덱스를 조회해 데이터 요청 주소를 얻고, 차원이 임베딩 차원과 같은지 확인한다.
// 인덱스는 자동으로 만들지 않는다 (요금이 드는 리소스라 처음 한 번 직접 만든다 → docs/NEWS_ARCHIVE.md)
func newPineconeClient(ctx context.Context, apiKey, index string, dimension int) (*pineconeClient, error) {
	c := &pineconeClient{apiKey: apiKey}
	var desc struct {
		Host      string `json:"host"`
		Dimension int    `json:"dimension"` // 2025-10 이전 버전
		// 2026-07부터 차원은 schema.fields의 dense_vector 필드에 있다
		Schema struct {
			Fields map[string]struct {
				Type      string `json:"type"`
				Dimension int    `json:"dimension"`
			} `json:"fields"`
		} `json:"schema"`
		Status struct {
			Ready bool `json:"ready"`
		} `json:"status"`
	}
	status, body, err := c.do(ctx, http.MethodGet, pineconeControlURL+"/indexes/"+url.PathEscape(index), nil)
	if err != nil {
		return nil, fmt.Errorf("Pinecone 인덱스 조회 실패: %w", err)
	}
	if status == http.StatusNotFound {
		return nil, fmt.Errorf("Pinecone 인덱스 %q가 없습니다. 처음 한 번 만들어야 합니다 (차원 %d, cosine)", index, dimension)
	}
	if status != http.StatusOK {
		return nil, fmt.Errorf("Pinecone 인덱스 조회 실패: 상태 코드 %d, 응답: %.300s", status, body)
	}
	if err := json.Unmarshal(body, &desc); err != nil {
		return nil, fmt.Errorf("Pinecone 인덱스 응답 해석 실패: %w", err)
	}
	if desc.Dimension == 0 {
		for _, f := range desc.Schema.Fields {
			if f.Type == "dense_vector" {
				desc.Dimension = f.Dimension
			}
		}
	}
	if desc.Dimension != dimension {
		return nil, fmt.Errorf("Pinecone 인덱스 차원(%d)이 임베딩 차원(%d)과 다릅니다", desc.Dimension, dimension)
	}
	if !desc.Status.Ready {
		return nil, fmt.Errorf("Pinecone 인덱스 %q가 아직 준비되지 않았습니다", index)
	}
	c.host = desc.Host
	return c, nil
}

// existingIDs 이미 저장된 id (다시 임베딩하지 않도록)
func (c *pineconeClient) existingIDs(ctx context.Context, namespace string, ids []string) (map[string]bool, error) {
	found := map[string]bool{}
	for start := 0; start < len(ids); start += pineconeFetchBatch {
		end := min(start+pineconeFetchBatch, len(ids))
		q := url.Values{"namespace": {namespace}}
		for _, id := range ids[start:end] {
			q.Add("ids", id)
		}
		var res struct {
			Vectors map[string]json.RawMessage `json:"vectors"`
		}
		if err := c.call(ctx, http.MethodGet, "/vectors/fetch?"+q.Encode(), nil, &res); err != nil {
			return nil, err
		}
		for id := range res.Vectors {
			found[id] = true
		}
	}
	return found, nil
}

type pineconeMatch struct {
	ID       string         `json:"id"`
	Score    float64        `json:"score"`
	Metadata map[string]any `json:"metadata"`
}

// query 가까운 벡터 topK개 (메타데이터 필터 적용, 유사도 높은 순)
func (c *pineconeClient) query(ctx context.Context, namespace string, vector []float32, topK int, filter map[string]any) ([]pineconeMatch, error) {
	body := map[string]any{"namespace": namespace, "vector": vector, "topK": topK, "includeMetadata": true}
	if len(filter) > 0 {
		body["filter"] = filter
	}
	var res struct {
		Matches []pineconeMatch `json:"matches"`
	}
	if err := c.call(ctx, http.MethodPost, "/query", body, &res); err != nil {
		return nil, err
	}
	return res.Matches, nil
}

func (c *pineconeClient) upsert(ctx context.Context, namespace string, vectors []pineconeVector) error {
	for start := 0; start < len(vectors); start += pineconeBatch {
		end := min(start+pineconeBatch, len(vectors))
		body := map[string]any{"namespace": namespace, "vectors": vectors[start:end]}
		if err := c.call(ctx, http.MethodPost, "/vectors/upsert", body, nil); err != nil {
			return err
		}
	}
	return nil
}

// listIDs id 접두어로 목록 (서버리스 인덱스에서 지원). 보관 기간이 지난 날짜의 기사를 지울 때 쓴다. prefix가 비면 전체
func (c *pineconeClient) listIDs(ctx context.Context, namespace, prefix string) ([]string, error) {
	var ids []string
	token := ""
	for {
		q := url.Values{"namespace": {namespace}, "limit": {"100"}}
		if prefix != "" {
			q.Set("prefix", prefix)
		}
		if token != "" {
			q.Set("paginationToken", token)
		}
		var res struct {
			Vectors []struct {
				ID string `json:"id"`
			} `json:"vectors"`
			Pagination *struct {
				Next string `json:"next"`
			} `json:"pagination"`
		}
		if err := c.call(ctx, http.MethodGet, "/vectors/list?"+q.Encode(), nil, &res); err != nil {
			return nil, err
		}
		for _, v := range res.Vectors {
			ids = append(ids, v.ID)
		}
		if res.Pagination == nil || res.Pagination.Next == "" {
			return ids, nil
		}
		token = res.Pagination.Next
	}
}

func (c *pineconeClient) deleteIDs(ctx context.Context, namespace string, ids []string) error {
	for start := 0; start < len(ids); start += pineconeBatch {
		end := min(start+pineconeBatch, len(ids))
		if err := c.call(ctx, http.MethodPost, "/vectors/delete", map[string]any{"namespace": namespace, "ids": ids[start:end]}, nil); err != nil {
			return err
		}
	}
	return nil
}

// call 인덱스 데이터 요청 (2xx가 아니면 에러)
func (c *pineconeClient) call(ctx context.Context, method, path string, in, out any) error {
	status, body, err := c.do(ctx, method, "https://"+c.host+path, in)
	if err != nil {
		return fmt.Errorf("Pinecone 요청 실패(%s): %w", path, err)
	}
	if status < 200 || status >= 300 {
		return fmt.Errorf("Pinecone 요청 실패(%s): 상태 코드 %d, 응답: %.300s", path, status, body)
	}
	if out != nil && len(body) > 0 {
		if err := json.Unmarshal(body, out); err != nil {
			return fmt.Errorf("Pinecone 응답 해석 실패(%s): %w", path, err)
		}
	}
	return nil
}

func (c *pineconeClient) do(ctx context.Context, method, url string, in any) (int, []byte, error) {
	reqCtx, cancel := context.WithTimeout(ctx, 30*time.Second)
	defer cancel()
	var reader io.Reader
	if in != nil {
		b, err := json.Marshal(in)
		if err != nil {
			return 0, nil, err
		}
		reader = bytes.NewReader(b)
	}
	req, err := http.NewRequestWithContext(reqCtx, method, url, reader)
	if err != nil {
		return 0, nil, err
	}
	req.Header.Set("Api-Key", c.apiKey)
	req.Header.Set("X-Pinecone-API-Version", pineconeAPIVersion)
	req.Header.Set("Accept", "application/json")
	if in != nil {
		req.Header.Set("Content-Type", "application/json")
	}
	resp, err := httpClient.Do(req)
	if err != nil {
		return 0, nil, err
	}
	defer resp.Body.Close()
	b, err := io.ReadAll(resp.Body)
	return resp.StatusCode, b, err
}
