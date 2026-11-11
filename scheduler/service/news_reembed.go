package service

import (
	"context"
	"log/slog"
	"net/http"
	"net/url"
	"strings"
	"time"
)

const (
	// reembedBatch 한 번에 읽고 다시 저장하는 기사 수
	reembedBatch = 50
	// 재시도까지 하고도 429가 이만큼 이어지면 하루 요청 한도가 다 찬 것으로 보고 멈춘다 (남은 기사는 다음에)
	reembedQuotaStop = 3
)

// ReembedNews 저장된 기사 전체를 지금 임베딩 형식으로 다시 임베딩한다 (한 번씩 손으로 실행: NEWS_REEMBED=1).
// 임베딩 모델이나 입력 형식이 바뀌면 예전 벡터와 새 벡터가 섞여 검색 품질이 고르지 않으므로 한꺼번에 맞춘다.
// 원문 대신 메타데이터의 제목·요약으로 임베딩하고, id와 메타데이터는 그대로 둔다. limit이 0보다 크면 그 개수만.
// 이미 지금 방식(embed_format)으로 만든 기사는 건너뛰므로 중간에 멈춰도 다시 실행하면 남은 것만 한다
func ReembedNews(ctx context.Context, cfg NewsArchiveConfig, limit int) (done, failed int, err error) {
	done, failed, _, err = reembedNews(ctx, cfg, limit)
	return done, failed, err
}

func reembedNews(ctx context.Context, cfg NewsArchiveConfig, limit int) (done, failed, skipped int, err error) {
	pc, err := newPineconeClient(ctx, cfg.PineconeAPIKey, cfg.PineconeIndex, cfg.EmbedDimension)
	if err != nil {
		return 0, 0, 0, err
	}
	ids, err := pc.listIDs(ctx, newsNamespace, "")
	if err != nil {
		return 0, 0, 0, err
	}
	format := embeddingFormat(cfg.EmbedModel)
	quotaFails := 0
	perMinute := cfg.EmbedPerMinute
	if perMinute <= 0 {
		perMinute = 80
	}
	ticker := time.NewTicker(time.Minute / time.Duration(perMinute))
	defer ticker.Stop()
	slog.InfoContext(ctx, "다시 임베딩 시작", "total", len(ids), "format", format, "limit", limit, "per_minute", perMinute)

	for start := 0; start < len(ids); start += reembedBatch {
		if limit > 0 && done >= limit {
			break
		}
		batch := ids[start:min(start+reembedBatch, len(ids))]
		metas, err := pc.fetchMetadata(ctx, newsNamespace, batch)
		if err != nil {
			return done, failed, skipped, err
		}
		var vectors []pineconeVector
		for _, id := range batch {
			meta, ok := metas[id]
			if !ok {
				failed++
				continue
			}
			if meta["embed_format"] == format {
				skipped++
				continue
			}
			if limit > 0 && done+len(vectors) >= limit {
				break
			}
			select {
			case <-ticker.C:
			case <-ctx.Done():
				return done, failed, skipped, ctx.Err()
			}
			title, _ := meta["title"].(string)
			snippet, _ := meta["snippet"].(string)
			values, err := embedText(ctx, cfg.GeminiAPIKey, cfg.EmbedModel, cfg.EmbedDimension, embedTaskDocument, title, snippet)
			if err != nil {
				slog.WarnContext(ctx, "다시 임베딩 실패, 건너뜀", "news_id", id, "error", err)
				failed++
				if strings.Contains(err.Error(), "429") {
					quotaFails++
				}
				if quotaFails >= reembedQuotaStop {
					// 지금까지 만든 것은 저장하고 멈춘다
					if err := pc.upsert(ctx, newsNamespace, vectors); err != nil {
						return done, failed, skipped, err
					}
					done += len(vectors)
					slog.WarnContext(ctx, "임베딩 요청 한도가 다 차서 멈춤, 한도가 풀린 뒤 다시 실행하면 남은 기사만 처리",
						"done", done, "skipped", skipped)
					return done, failed, skipped, nil
				}
				continue
			}
			quotaFails = 0
			meta["embed_format"] = format
			vectors = append(vectors, pineconeVector{ID: id, Values: values, Metadata: meta})
		}
		if err := pc.upsert(ctx, newsNamespace, vectors); err != nil {
			return done, failed, skipped, err
		}
		done += len(vectors)
		slog.InfoContext(ctx, "다시 임베딩 진행", "done", done, "failed", failed, "skipped", skipped, "total", len(ids))
	}
	return done, failed, skipped, nil
}

// fetchMetadata id별 메타데이터
func (c *pineconeClient) fetchMetadata(ctx context.Context, namespace string, ids []string) (map[string]map[string]any, error) {
	q := url.Values{"namespace": {namespace}}
	for _, id := range ids {
		q.Add("ids", id)
	}
	var res struct {
		Vectors map[string]struct {
			Metadata map[string]any `json:"metadata"`
		} `json:"vectors"`
	}
	if err := c.call(ctx, http.MethodGet, "/vectors/fetch?"+q.Encode(), nil, &res); err != nil {
		return nil, err
	}
	out := make(map[string]map[string]any, len(res.Vectors))
	for id, v := range res.Vectors {
		out[id] = v.Metadata
	}
	return out, nil
}
