package service

import (
	"context"
	"crypto/sha1"
	"encoding/hex"
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"regexp"
	"sort"
	"strings"
	"sync"
	"time"
	"unicode"
	"unicode/utf8"
)

// 뉴스 아카이브: 매시간 네이버 뉴스를 모아 Gemini 임베딩으로 Pinecone에 쌓는다.
// 일일 인사이트가 '최근 30일 맥락'과 '비슷한 과거 사건'을 찾을 때 쓰는 저장소다 (검색 연결은 다음 단계).
//
// 저장하는 것: 기사 제목, 검색 결과 요약(본문 아님), 링크, 언론사 도메인, 발행 시각, 관련 코인, 검색어.
// id는 'n-{발행일 KST}-{링크 해시}'라서 같은 기사는 한 번만 저장되고, 보관 기간이 지난 날짜는 접두어로 찾아 지운다.

const (
	newsNamespace = "news"
	// 보관 기간 (로그·접속 기록과 같은 3개월)
	newsRetentionDays = 90
	// 이보다 오래된 기사는 모으지 않는다 (몇 시간 실행이 빠져도 메울 수 있게 넉넉히)
	newsCollectWindow = 48 * time.Hour
	// 검색어마다 받는 기사 수 (네이버 최대 100)
	newsPerQuery = 50
	// 한 번 실행에서 새로 임베딩하는 최대 기사 수 (남은 기사는 다음 실행에서)
	newsMaxEmbedPerRun = 100
	// 동시에 보내는 임베딩 요청 수
	newsEmbedConcurrency = 3
	// 메타데이터에 남기는 요약 길이 (검색 결과의 요약도 이 길이로 자른다)
	newsSnippetRunes = 200
)

// 매시간 검색하는 말. 코인별 기사는 결과에서 이름·티커로 태그를 단다
var newsArchiveQueries = []string{"가상자산", "암호화폐", "비트코인", "이더리움", "리플", "솔라나", "업비트", "코인 시세"}

// NewsArchiveConfig 뉴스 아카이브 설정. 키가 하나라도 없으면 실행하지 않는다
type NewsArchiveConfig struct {
	GeminiAPIKey      string
	EmbedModel        string
	EmbedDimension    int
	NaverClientID     string
	NaverClientSecret string
	PineconeAPIKey    string
	PineconeIndex     string
	// 분당 임베딩 요청 상한 (0이면 기본 80). 첫 실행에서 몰아 보내다 요청 한도(429)에 걸려 줄였다
	EmbedPerMinute int
}

func (c NewsArchiveConfig) Enabled() bool {
	return c.GeminiAPIKey != "" && c.NaverClientID != "" && c.NaverClientSecret != "" && c.PineconeAPIKey != "" && c.PineconeIndex != ""
}

// archivedNews 저장할 기사 한 건
type archivedNews struct {
	ID      string
	Article naverArticle
	Query   string
	Symbols []string
}

// listedCoin 태그를 달 때 쓰는 상장 코인 (이름·티커)
type listedCoin struct {
	Symbol, Name, Ticker string
}

// ArchiveNews 새 기사를 모아 임베딩해 저장한다. prune이면 보관 기간이 지난 날짜도 지운다 (하루 한 번)
func ArchiveNews(ctx context.Context, cfg NewsArchiveConfig, symbolMap map[string]int, now time.Time, prune bool) error {
	pc, err := newPineconeClient(ctx, cfg.PineconeAPIKey, cfg.PineconeIndex, cfg.EmbedDimension)
	if err != nil {
		return err
	}

	coins, err := listedCoins(ctx, symbolMap)
	if err != nil {
		slog.WarnContext(ctx, "상장 코인 목록 조회 실패, 코인 태그 없이 진행", "error", err)
	}

	candidates, err := collectArchiveCandidates(ctx, cfg, coins, now)
	if err != nil {
		return err
	}

	ids := make([]string, len(candidates))
	for i, c := range candidates {
		ids[i] = c.ID
	}
	existing, err := pc.existingIDs(ctx, newsNamespace, ids)
	if err != nil {
		return err
	}
	var fresh []archivedNews
	for _, c := range candidates {
		if !existing[c.ID] {
			fresh = append(fresh, c)
		}
	}
	// 오래된 것부터 저장해 상한에 걸려도 빈 구간이 생기지 않게
	sort.Slice(fresh, func(i, j int) bool { return fresh[i].Article.PublishedAt.Before(fresh[j].Article.PublishedAt) })
	if len(fresh) > newsMaxEmbedPerRun {
		fresh = fresh[:newsMaxEmbedPerRun]
	}

	vectors := embedArchiveNews(ctx, cfg, fresh, now)
	if err := pc.upsert(ctx, newsNamespace, vectors); err != nil {
		return err
	}
	slog.InfoContext(ctx, "뉴스 아카이브 저장",
		"candidates", len(candidates), "existing", len(existing), "saved", len(vectors), "fresh", len(fresh))
	if len(fresh) > 0 && len(vectors) == 0 {
		return errors.New("뉴스 아카이브: 새 기사를 하나도 임베딩하지 못했습니다")
	}

	if prune {
		deleted, err := pruneNewsArchive(ctx, pc, now)
		if err != nil {
			return err
		}
		slog.InfoContext(ctx, "보관 기간 지난 기사 삭제", "retention_days", newsRetentionDays, "deleted", deleted)
	}
	return nil
}

// listedCoins 우리 서비스에 상장된 원화 코인의 이름·티커
func listedCoins(ctx context.Context, symbolMap map[string]int) ([]listedCoin, error) {
	list, err := getSymbolList(ctx)
	if err != nil {
		return nil, err
	}
	var coins []listedCoin
	for _, c := range list {
		if _, ok := symbolMap[c.Market]; ok {
			coins = append(coins, listedCoin{Symbol: c.Market, Name: c.KoreanName, Ticker: strings.TrimPrefix(c.Market, "KRW-")})
		}
	}
	return coins, nil
}

// collectArchiveCandidates 검색어마다 최근 기사를 받아 가상자산 기사만 남기고 같은 기사(같은 id)를 합친다
func collectArchiveCandidates(ctx context.Context, cfg NewsArchiveConfig, coins []listedCoin, now time.Time) ([]archivedNews, error) {
	byID := map[string]archivedNews{}
	failed := 0
	for _, q := range newsArchiveQueries {
		articles, err := searchNaverNews(ctx, cfg.NaverClientID, cfg.NaverClientSecret, q, newsPerQuery)
		if err != nil {
			slog.WarnContext(ctx, "뉴스 검색 실패", "query", q, "error", err)
			failed++
			continue
		}
		for _, a := range articles {
			if now.Sub(a.PublishedAt) > newsCollectWindow || a.PublishedAt.After(now.Add(time.Hour)) {
				continue
			}
			if !isCryptoArticle(a) {
				continue
			}
			id := newsID(a, now.Location())
			if _, ok := byID[id]; ok {
				continue
			}
			byID[id] = archivedNews{ID: id, Article: a, Query: q, Symbols: tagSymbols(a.Title+" "+a.Description, coins)}
		}
	}
	if failed == len(newsArchiveQueries) {
		return nil, errors.New("뉴스 아카이브: 네이버 뉴스 검색이 모두 실패했습니다")
	}
	out := make([]archivedNews, 0, len(byID))
	for _, c := range byID {
		out = append(out, c)
	}
	return out, nil
}

// isCryptoArticle 제목에 가상자산 단어가 있거나, 요약에 서로 다른 가상자산 단어가 두 개 이상 있는 기사.
// 요약에 한 번 스치듯 나오는 일반 기사(선거·외교·부동산 등)는 뺀다 (시험 수집에서 260건 중 상당수가 이런 기사였음)
func isCryptoArticle(a naverArticle) bool {
	if containsAny(a.Title, cryptoContext) {
		return true
	}
	lower := strings.ToLower(a.Description)
	hits := 0
	for _, w := range cryptoContext {
		if strings.Contains(lower, strings.ToLower(w)) {
			hits++
		}
	}
	return hits >= 2
}

// newsID 'n-20261104-{링크 sha1 앞 16자}' (발행일은 KST 기준)
func newsID(a naverArticle, loc *time.Location) string {
	sum := sha1.Sum([]byte(a.URL))
	return "n-" + a.PublishedAt.In(loc).Format("20060102") + "-" + hex.EncodeToString(sum[:])[:16]
}

// tagSymbols 제목·요약에 코인 이름이나 티커가 나오면 그 코인을 태그한다.
// 이름이 다른 낱말 속에 들어간 경우('엔지니어' 속 '니어')는 앞 글자가 한글이면 건너뛴다. 티커는 3자 이상 대문자 단어만 본다
func tagSymbols(text string, coins []listedCoin) []string {
	var symbols []string
	for _, c := range coins {
		if containsKoreanWord(text, c.Name) || (len(c.Ticker) >= 3 && tickerPattern(c.Ticker).MatchString(text)) {
			symbols = append(symbols, c.Symbol)
		}
	}
	sort.Strings(symbols)
	return symbols
}

var tickerCache sync.Map

func tickerPattern(ticker string) *regexp.Regexp {
	if p, ok := tickerCache.Load(ticker); ok {
		return p.(*regexp.Regexp)
	}
	p := regexp.MustCompile(`(^|[^A-Za-z0-9])` + regexp.QuoteMeta(ticker) + `($|[^A-Za-z0-9])`)
	tickerCache.Store(ticker, p)
	return p
}

// containsKoreanWord 낱말 앞이 한글이 아닌 곳에 나오는지 (뒤에 조사가 붙는 것은 괜찮다)
func containsKoreanWord(text, word string) bool {
	if word == "" {
		return false
	}
	for i := 0; ; {
		j := strings.Index(text[i:], word)
		if j < 0 {
			return false
		}
		pos := i + j
		prev, _ := utf8.DecodeLastRuneInString(text[:pos])
		if pos == 0 || !unicode.Is(unicode.Hangul, prev) {
			return true
		}
		i = pos + len(word)
	}
}

// embedArchiveNews 기사마다 '제목 + 요약'을 임베딩한다. 실패한 기사는 빼고 (다음 실행에서 다시 시도)
func embedArchiveNews(ctx context.Context, cfg NewsArchiveConfig, news []archivedNews, now time.Time) []pineconeVector {
	results := make([]*pineconeVector, len(news))
	sem := make(chan struct{}, newsEmbedConcurrency)
	perMinute := cfg.EmbedPerMinute
	if perMinute <= 0 {
		perMinute = 80
	}
	// 모든 작업자가 함께 쓰는 간격 조절 (분당 perMinute회)
	ticker := time.NewTicker(time.Minute / time.Duration(perMinute))
	defer ticker.Stop()
	var wg sync.WaitGroup
	for i, n := range news {
		wg.Go(func() {
			sem <- struct{}{}
			defer func() { <-sem }()
			select {
			case <-ticker.C:
			case <-ctx.Done():
				return
			}
			values, err := embedText(ctx, cfg.GeminiAPIKey, cfg.EmbedModel, cfg.EmbedDimension, embedTaskDocument,
				n.Article.Title, n.Article.Description)
			if err != nil {
				slog.WarnContext(ctx, "기사 임베딩 실패, 건너뜀", "news_id", n.ID, "error", err)
				return
			}
			meta := newsMetadata(n, now.Location())
			meta["embed_format"] = embeddingFormat(cfg.EmbedModel)
			results[i] = &pineconeVector{ID: n.ID, Values: values, Metadata: meta}
		})
	}
	wg.Wait()

	var vectors []pineconeVector
	for _, v := range results {
		if v != nil {
			vectors = append(vectors, *v)
		}
	}
	return vectors
}

func newsMetadata(n archivedNews, loc *time.Location) map[string]any {
	source := ""
	if u, err := url.Parse(n.Article.URL); err == nil {
		source = strings.TrimPrefix(u.Hostname(), "www.")
	}
	symbols := n.Symbols
	if symbols == nil {
		symbols = []string{}
	}
	return map[string]any{
		"title":        n.Article.Title,
		"snippet":      truncateRunes(n.Article.Description, newsSnippetRunes),
		"url":          n.Article.URL,
		"source":       source,
		"published_at": n.Article.PublishedAt.Unix(), // 기간 필터용 (초)
		"published":    n.Article.PublishedAt.In(loc).Format("2006-01-02 15:04"),
		"symbols":      symbols,
		"query":        n.Query,
	}
}

func truncateRunes(s string, n int) string {
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	return string([]rune(s)[:n])
}

// pruneNewsArchive 보관 기간이 지난 날짜(90~96일 전, 하루 실행이 빠져도 지울 수 있게 일주일)의 기사를 지운다
func pruneNewsArchive(ctx context.Context, pc *pineconeClient, now time.Time) (int, error) {
	deleted := 0
	for d := newsRetentionDays; d < newsRetentionDays+7; d++ {
		prefix := "n-" + now.AddDate(0, 0, -d).Format("20060102") + "-"
		ids, err := pc.listIDs(ctx, newsNamespace, prefix)
		if err != nil {
			return deleted, fmt.Errorf("뉴스 아카이브 정리 실패(%s): %w", prefix, err)
		}
		if err := pc.deleteIDs(ctx, newsNamespace, ids); err != nil {
			return deleted, err
		}
		deleted += len(ids)
	}
	return deleted, nil
}
