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

// 가상자산 기사인지 가르는 단어. 코인 이름이 일반 명사·회사명과 겹칠 때(예: '소폰' 디자인 스튜디오) 엉뚱한 기사를 거른다
var cryptoContext = []string{"코인", "가상자산", "암호화폐", "가상화폐", "비트코인", "블록체인", "토큰", "거래소", "업비트", "빗썸", "시세", "상장"}

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

// fetchNews 최신순 검색 결과에서 최근 기사 newsPerTarget개. idPrefix는 인용 ID 앞부분 (예: "n1-")
func fetchNews(ctx context.Context, clientID, clientSecret, query string, mustContain []string, now time.Time, idPrefix string) ([]NewsItem, error) {
	u := naverNewsURL + "?" + url.Values{
		"query":   {query},
		"display": {"20"},
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

	var items []NewsItem
	for _, it := range res.Items {
		published, err := time.Parse(time.RFC1123Z, it.PubDate)
		if err != nil || now.Sub(published) > newsMaxAge || published.After(now.Add(time.Hour)) {
			continue
		}
		title := cleanNewsText(it.Title)
		desc := cleanNewsText(it.Description)
		if !containsAny(title+" "+desc, mustContain) || !containsAny(title+" "+desc, cryptoContext) {
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
		items = append(items, NewsItem{
			ID:          fmt.Sprintf("%s%d", idPrefix, len(items)+1),
			Title:       title,
			URL:         link,
			PublishedAt: published.In(now.Location()).Format("2006-01-02 15:04"),
			Description: desc,
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
