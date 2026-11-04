package service

import (
	"context"
	"fmt"
	"html"
	"net/url"
	"regexp"
	"strings"
	"time"
)

// 네이버 뉴스 검색 (NAVER API HUB). 인사이트의 근거 기사로 쓰고, 화면에 '참고 기사'로 링크를 보여준다.
// 기사 본문은 저장하지 않고 제목·링크·발행 시각만 남긴다.

const (
	naverNewsURL = "https://naverapihub.apigw.ntruss.com/search/v1/news"
	// 코인 하나에 넣는 기사 수
	newsPerTarget = 4
	// 이보다 오래된 기사는 '최근 소식'으로 보지 않는다
	newsMaxAge = 36 * time.Hour
)

var htmlTag = regexp.MustCompile(`<[^>]+>`)

// 가상자산 기사인지 가르는 단어. 코인 이름이 일반 명사·회사명과 겹칠 때(예: '소폰' 디자인 스튜디오) 엉뚱한 기사를 거른다.
// '거래소', '상장', '시세', '토큰'은 주식·일반 기사에도 흔해서 넣지 않는다 (뉴스 아카이브 시험 수집에서 게임·선거 기사가 섞임)
var cryptoContext = []string{"코인", "가상자산", "암호화폐", "가상화폐", "디지털자산", "비트코인", "이더리움", "블록체인", "업비트", "빗썸", "코인원", "바이낸스", "코인베이스", "스테이블코인", "알트코인", "테더", "USDT", "BTC", "ETH"}

// NewsItem 모델에 넘기는 기사 (ID로 인용하게 하고, 저장·표시할 때는 제목·링크·시각만 쓴다)
type NewsItem struct {
	ID          string `json:"id"`
	Title       string `json:"title"`
	URL         string `json:"url"`
	PublishedAt string `json:"publishedAt"`
	Description string `json:"-"`
}

type naverNewsResponse struct {
	Items []struct {
		Title        string `json:"title"`
		OriginalLink string `json:"originallink"`
		Link         string `json:"link"`
		Description  string `json:"description"`
		PubDate      string `json:"pubDate"`
	} `json:"items"`
}

// newsKeywords 코인 이름으로 검색하고, 결과 중 이름이나 티커가 제목·요약에 보이는 기사만 남긴다 (가상자산 맥락 단어도 함께 확인)
func newsKeywords(d coinData) (query string, mustContain []string) {
	return d.Name, []string{d.Name, strings.TrimPrefix(d.Symbol, "KRW-")}
}

// naverArticle 정리된 검색 결과 한 건 (태그·엔티티 제거, http(s) 링크만)
type naverArticle struct {
	Title       string
	Description string
	URL         string
	PublishedAt time.Time
}

// searchNaverNews 최신순 검색 결과를 정리해 돌려준다 (display 최대 100)
func searchNaverNews(ctx context.Context, clientID, clientSecret, query string, display int) ([]naverArticle, error) {
	u := naverNewsURL + "?" + url.Values{
		"query":   {query},
		"display": {fmt.Sprint(display)},
		"sort":    {"date"},
	}.Encode()

	var res naverNewsResponse
	err := getJSONWithHeaders(ctx, u, 10*time.Second, map[string]string{
		"X-NCP-APIGW-API-KEY-ID": clientID,
		"X-NCP-APIGW-API-KEY":    clientSecret,
	}, &res)
	if err != nil {
		return nil, fmt.Errorf("네이버 뉴스 검색 실패(%s): %w", query, err)
	}

	var articles []naverArticle
	for _, it := range res.Items {
		published, err := time.Parse(time.RFC1123Z, it.PubDate)
		if err != nil {
			continue
		}
		link := it.OriginalLink
		if link == "" {
			link = it.Link
		}
		// 화면에 링크로 걸리므로 http(s) 주소만 받는다
		if !strings.HasPrefix(link, "https://") && !strings.HasPrefix(link, "http://") {
			continue
		}
		articles = append(articles, naverArticle{
			Title:       cleanNewsText(it.Title),
			Description: cleanNewsText(it.Description),
			URL:         link,
			PublishedAt: published,
		})
	}
	return articles, nil
}

// fetchNews 인사이트용: 최신순 검색 결과에서 최근 기사 newsPerTarget개. idPrefix는 인용 ID 앞부분 (예: "n1-")
func fetchNews(ctx context.Context, clientID, clientSecret, query string, mustContain []string, now time.Time, idPrefix string) ([]NewsItem, error) {
	articles, err := searchNaverNews(ctx, clientID, clientSecret, query, 20)
	if err != nil {
		return nil, err
	}
	var items []NewsItem
	for _, a := range articles {
		if now.Sub(a.PublishedAt) > newsMaxAge || a.PublishedAt.After(now.Add(time.Hour)) {
			continue
		}
		if !containsAny(a.Title+" "+a.Description, mustContain) || !containsAny(a.Title+" "+a.Description, cryptoContext) {
			continue
		}
		items = append(items, NewsItem{
			ID:          fmt.Sprintf("%s%d", idPrefix, len(items)+1),
			Title:       a.Title,
			URL:         a.URL,
			PublishedAt: a.PublishedAt.In(now.Location()).Format("2006-01-02 15:04"),
			Description: a.Description,
		})
		if len(items) == newsPerTarget {
			break
		}
	}
	return items, nil
}

// cleanNewsText 검색어 강조 태그(<b>)와 HTML 엔티티(&quot; 등)를 걷어낸다
func cleanNewsText(s string) string {
	return strings.TrimSpace(html.UnescapeString(htmlTag.ReplaceAllString(s, "")))
}

func containsAny(s string, words []string) bool {
	lower := strings.ToLower(s)
	for _, w := range words {
		if w != "" && strings.Contains(lower, strings.ToLower(w)) {
			return true
		}
	}
	return false
}
