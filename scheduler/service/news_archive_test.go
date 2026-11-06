package service

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

func TestNewsIDIsStablePerArticle(t *testing.T) {
	kst := time.FixedZone("KST", 9*3600)
	// 2026-11-03 23:30 UTC = 2026-11-04 08:30 KST → 발행일은 KST 기준
	a := naverArticle{URL: "https://example.com/a", PublishedAt: time.Date(2026, 11, 3, 23, 30, 0, 0, time.UTC)}
	id := newsID(a, kst)
	if !strings.HasPrefix(id, "n-20261104-") || len(id) != len("n-20261104-")+16 {
		t.Errorf("newsID() = %s", id)
	}
	if newsID(a, kst) != id {
		t.Error("같은 기사는 같은 id여야 함 (중복 저장 방지)")
	}
	if newsID(naverArticle{URL: "https://example.com/b", PublishedAt: a.PublishedAt}, kst) == id {
		t.Error("다른 기사는 다른 id여야 함")
	}
}

func TestTagSymbols(t *testing.T) {
	coins := []listedCoin{
		{Symbol: "KRW-BTC", Name: "비트코인", Ticker: "BTC"},
		{Symbol: "KRW-NEAR", Name: "니어프로토콜", Ticker: "NEAR"},
		{Symbol: "KRW-XRP", Name: "리플", Ticker: "XRP"},
		{Symbol: "KRW-SOL", Name: "솔라나", Ticker: "SOL"},
		{Symbol: "KRW-T", Name: "쓰레스홀드", Ticker: "T"},
	}
	cases := map[string]string{
		"비트코인과 XRP가 동반 상승":        "KRW-BTC,KRW-XRP",
		"리플이 급등했다":                "KRW-XRP",  // 뒤에 조사는 괜찮다
		"트리플 크라운 달성":              "",         // '트리플' 속 '리플'은 아님
		"NEAR 생태계 확장, SOLANA 아님":  "KRW-NEAR", // SOLANA 속 SOL은 아님
		"비트코인(BTC) 9만 달러":         "KRW-BTC",
		"T 티커 같은 짧은 티커는 이름으로만 판단": "",
	}
	for text, want := range cases {
		if got := strings.Join(tagSymbols(text, coins), ","); got != want {
			t.Errorf("tagSymbols(%q) = %q, want %q", text, got, want)
		}
	}
}

func TestIsCryptoArticle(t *testing.T) {
	cases := []struct {
		title, desc string
		want        bool
	}{
		{"내 코인 해킹당하면 거래소가 다 물어줘야 할까", "", true},
		{"북한 해커, 바이낸스 직원 속여 악성코드", "가상자산 거래소 직원을 노렸다", true},
		{"김여정 \"미국과의 대화\"", "북한은 암호화폐 탈취 의혹을 부인했다", false}, // 스치듯 한 번
		{"미국 중간선거 결과에 월가 촉각", "주식과 가상자산 시장이 결과를 주시한다", false},
		{"재정정책 콘퍼런스 개최", "블록체인과 스테이블코인, 암호화폐 규제를 논의했다", true},
	}
	for _, c := range cases {
		if got := isCryptoArticle(naverArticle{Title: c.title, Description: c.desc}); got != c.want {
			t.Errorf("isCryptoArticle(%q) = %v, want %v", c.title, got, c.want)
		}
	}
}

func TestNewsMetadata(t *testing.T) {
	kst := time.FixedZone("KST", 9*3600)
	n := archivedNews{
		ID:    "n-20261104-x",
		Query: "비트코인",
		Article: naverArticle{
			Title: "제목", URL: "https://www.tokenpost.kr/news/1", Description: strings.Repeat("가", 300),
			PublishedAt: time.Date(2026, 11, 4, 0, 30, 0, 0, kst),
		},
	}
	m := newsMetadata(n, kst)
	if m["source"] != "tokenpost.kr" || m["published"] != "2026-11-04 00:30" || len([]rune(m["snippet"].(string))) != newsSnippetRunes {
		t.Errorf("newsMetadata() = %+v", m)
	}
	if syms, ok := m["symbols"].([]string); !ok || syms == nil {
		t.Error("태그가 없어도 빈 배열이어야 함 (Pinecone 필터용)")
	}
}

// 가짜 Pinecone으로 인덱스 확인, 중복 확인, 나눠 보내기, 목록 페이지 넘기기, 삭제를 확인한다
func TestPineconeClient(t *testing.T) {
	var upserted, deleted int
	var upsertCalls int
	srv := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("Api-Key") != "key" || r.Header.Get("X-Pinecone-API-Version") == "" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		switch {
		case r.URL.Path == "/indexes/bitground-news":
			// API 2026-07 응답: 차원이 schema.fields 안에 있다
			_ = json.NewEncoder(w).Encode(map[string]any{"host": strings.TrimPrefix(srvURL, "https://"), "status": map[string]any{"ready": true},
				"schema": map[string]any{"fields": map[string]any{"_values": map[string]any{"type": "dense_vector", "dimension": 768, "metric": "cosine"}}}})
		case r.URL.Path == "/indexes/wrong-dim":
			_ = json.NewEncoder(w).Encode(map[string]any{"host": "x", "dimension": 3072, "status": map[string]any{"ready": true}})
		case r.URL.Path == "/vectors/fetch":
			vectors := map[string]any{}
			for _, id := range r.URL.Query()["ids"] {
				if id == "n-1" {
					vectors[id] = map[string]any{"id": id}
				}
			}
			_ = json.NewEncoder(w).Encode(map[string]any{"vectors": vectors})
		case r.URL.Path == "/vectors/upsert":
			var body struct {
				Vectors []pineconeVector `json:"vectors"`
			}
			_ = json.NewDecoder(r.Body).Decode(&body)
			upserted += len(body.Vectors)
			upsertCalls++
		case r.URL.Path == "/vectors/list":
			if r.URL.Query().Get("paginationToken") == "" {
				_ = json.NewEncoder(w).Encode(map[string]any{"vectors": []map[string]string{{"id": "n-old-1"}}, "pagination": map[string]string{"next": "p2"}})
			} else {
				_ = json.NewEncoder(w).Encode(map[string]any{"vectors": []map[string]string{{"id": "n-old-2"}}})
			}
		case r.URL.Path == "/vectors/delete":
			var body struct {
				IDs []string `json:"ids"`
			}
			_ = json.NewDecoder(r.Body).Decode(&body)
			deleted += len(body.IDs)
		default:
			w.WriteHeader(http.StatusNotFound)
		}
	}))
	defer srv.Close()
	srvURL = srv.URL
	oldControl, oldClient := pineconeControlURL, httpClient
	pineconeControlURL, httpClient = srv.URL, srv.Client()
	defer func() { pineconeControlURL, httpClient = oldControl, oldClient }()

	ctx := context.Background()
	if _, err := newPineconeClient(ctx, "key", "wrong-dim", 768); err == nil || !strings.Contains(err.Error(), "차원") {
		t.Errorf("차원이 다르면 실패해야 함: %v", err)
	}
	if _, err := newPineconeClient(ctx, "key", "missing", 768); err == nil || !strings.Contains(err.Error(), "없습니다") {
		t.Errorf("인덱스가 없으면 안내와 함께 실패해야 함: %v", err)
	}
	pc, err := newPineconeClient(ctx, "key", "bitground-news", 768)
	if err != nil {
		t.Fatal(err)
	}

	existing, err := pc.existingIDs(ctx, newsNamespace, []string{"n-1", "n-2"})
	if err != nil || !existing["n-1"] || existing["n-2"] {
		t.Errorf("existingIDs() = %v, %v", existing, err)
	}

	vectors := make([]pineconeVector, 250)
	for i := range vectors {
		vectors[i] = pineconeVector{ID: "v", Values: []float32{1}}
	}
	if err := pc.upsert(ctx, newsNamespace, vectors); err != nil || upserted != 250 || upsertCalls != 3 {
		t.Errorf("upsert: %d건 %d번, %v (100건씩 3번이어야 함)", upserted, upsertCalls, err)
	}

	ids, err := pc.listIDs(ctx, newsNamespace, "n-old")
	if err != nil || strings.Join(ids, ",") != "n-old-1,n-old-2" {
		t.Errorf("listIDs() = %v, %v (다음 페이지까지)", ids, err)
	}
	if err := pc.deleteIDs(ctx, newsNamespace, ids); err != nil || deleted != 2 {
		t.Errorf("deleteIDs: %d, %v", deleted, err)
	}
}

var srvURL string

func TestEmbeddingRequest(t *testing.T) {
	text := func(req map[string]any) string {
		return req["content"].(map[string]any)["parts"].([]map[string]string)[0]["text"]
	}
	// gemini-embedding-2: taskType 없이 용도를 글 앞에 적는다
	doc := embeddingRequest("gemini-embedding-2", 768, embedTaskDocument, "비트코인 급등", "본문")
	if _, ok := doc["taskType"]; ok || text(doc) != "title: 비트코인 급등 | text: 본문" || doc["outputDimensionality"] != 768 {
		t.Errorf("문서 요청 = %v", doc)
	}
	query := embeddingRequest("gemini-embedding-2", 768, embedTaskQuery, "", "비트코인 전망")
	if _, ok := query["taskType"]; ok || text(query) != "task: search result | query: 비트코인 전망" {
		t.Errorf("검색어 요청 = %v", query)
	}
	// 이전 모델은 taskType 필드
	old := embeddingRequest("gemini-embedding-001", 768, embedTaskQuery, "", "비트코인 전망")
	if old["taskType"] != embedTaskQuery || text(old) != "비트코인 전망" {
		t.Errorf("gemini-embedding-001 요청 = %v", old)
	}
}
