package service

import (
	"context"
	"fmt"
	"log/slog"
	"strings"
	"time"
)

// 인사이트의 배경 자료: 뉴스 아카이브(Pinecone)에서 최근 30일 관련 기사를 찾아 <context> 블록으로 넣는다.
// 오늘 기사(<news>)는 오늘 움직임의 근거, 이 기사들은 이어지는 흐름·배경으로만 쓰게 한다 (프롬프트 daily-insight-v4).
// Pinecone 설정이 없거나 검색이 실패하면 배경 자료 없이 진행한다.

const (
	contextDays = 30
	// 프롬프트에 넣는 배경 기사 수
	contextTopK = 4
	// 이보다 덜 비슷한 기사는 넣지 않는다 (첫 수집 확인에서 관련 기사는 0.77~0.80)
	contextMinScore = 0.65
)

type contextRetriever struct {
	pc        *pineconeClient
	apiKey    string
	model     string
	dimension int
}

// newContextRetriever 설정이 없거나 인덱스를 못 쓰면 nil (배경 자료 없이 진행)
func newContextRetriever(ctx context.Context, ic InsightConfig) *contextRetriever {
	if ic.PineconeAPIKey == "" || ic.PineconeIndex == "" || ic.EmbedModel == "" || ic.EmbedDimension == 0 {
		return nil
	}
	pc, err := newPineconeClient(ctx, ic.PineconeAPIKey, ic.PineconeIndex, ic.EmbedDimension)
	if err != nil {
		slog.WarnContext(ctx, "뉴스 아카이브를 쓸 수 없어 배경 자료 없이 진행", "error", err)
		return nil
	}
	return &contextRetriever{pc: pc, apiKey: ic.GeminiAPIKey, model: ic.EmbedModel, dimension: ic.EmbedDimension}
}

// contextQuery 검색어: 대상 이름과 오늘 기사 제목 (오늘 이야기와 이어지는 과거 기사를 찾도록)
func contextQuery(subject string, news []NewsItem) string {
	parts := []string{subject + " 관련 소식과 가격 흐름"}
	for _, n := range news {
		parts = append(parts, n.Title)
	}
	return strings.Join(parts, "\n")
}

// related 최근 24시간 이전 30일 동안의 기사 중 비슷한 것. symbol이 있으면 그 코인 태그가 붙은 기사만. 오늘 기사와 같은 링크·제목은 뺀다
func (r *contextRetriever) related(ctx context.Context, query, symbol string, today []NewsItem, now time.Time, idPrefix string) []NewsItem {
	if r == nil {
		return nil
	}
	vector, err := embedText(ctx, r.apiKey, r.model, r.dimension, embedTaskQuery, "", query)
	if err != nil {
		slog.WarnContext(ctx, "배경 자료 검색어 임베딩 실패", "symbol", symbol, "error", err)
		return nil
	}
	// 인사이트가 다루는 최근 24시간보다 앞선 기사만 (오늘 기사는 <news>로 따로 들어가고, 배경은 '지난' 흐름이어야 한다)
	filter := map[string]any{"published_at": map[string]any{
		"$gte": now.AddDate(0, 0, -contextDays).Unix(),
		"$lt":  now.Add(-24 * time.Hour).Unix(),
	}}
	if symbol != "" {
		filter["symbols"] = map[string]any{"$in": []string{symbol}}
	}
	matches, err := r.pc.query(ctx, newsNamespace, vector, contextTopK*3, filter)
	if err != nil {
		slog.WarnContext(ctx, "배경 자료 검색 실패", "symbol", symbol, "error", err)
		return nil
	}
	return pickContext(matches, today, idPrefix)
}

// pickContext 유사도 기준을 넘고 오늘 기사·서로와 겹치지 않는 기사 contextTopK개 (유사도 높은 순)
func pickContext(matches []pineconeMatch, today []NewsItem, idPrefix string) []NewsItem {
	seen := map[string]bool{}
	for _, n := range today {
		seen[n.URL] = true
		seen[n.Title] = true
	}
	var items []NewsItem
	for _, m := range matches {
		if m.Score < contextMinScore {
			continue
		}
		title, _ := m.Metadata["title"].(string)
		url, _ := m.Metadata["url"].(string)
		if title == "" || seen[url] || seen[title] {
			continue
		}
		seen[url], seen[title] = true, true
		published, _ := m.Metadata["published"].(string)
		snippet, _ := m.Metadata["snippet"].(string)
		items = append(items, NewsItem{
			ID:          fmt.Sprintf("%s%d", idPrefix, len(items)+1),
			Title:       title,
			URL:         url,
			PublishedAt: published,
			Description: snippet,
		})
		if len(items) == contextTopK {
			break
		}
	}
	return items
}
