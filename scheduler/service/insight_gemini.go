package service

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"math"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"
	"unicode/utf8"
)

// 일일 인사이트 생성 (Gemini). 숫자·분위기 라벨·대상 선정은 코드가 하고, 모델은 받은 자료로 해설만 쓴다.
// 규칙(system)과 응답 스키마는 prompts/{버전}/ 파일에 있다 (insight_prompt.go)

// 투자 권유·가격 예측으로 읽히는 표현
var forbiddenInsight = regexp.MustCompile(`(매수|매도|구매|판매)(하세요|하십시오|를 추천|을 추천|를 권|을 권|할 만)|추천합니다|목표가|목표 가격|오를 것|떨어질 것|상승할 것|하락할 것|반드시|확실히`)

// 본문에 나온 퍼센트 값 (입력에 있는 값인지 대조). 앞뒤 숫자까지 통째로 잡아 '1.02%' 안의 '2%' 같은 부분 일치를 막는다
var percentToken = regexp.MustCompile(`\d+(?:\.\d+)?%`)

// 숫자를 말로 풀어 쓴 표현 ('마이너스 1.58퍼센트')
var spelledNumber = regexp.MustCompile(`퍼센트|(플러스|마이너스)\s?\d`)

// '마이너스 1.78퍼센트', '마이너스 1.78%', '1.78 퍼센트' → '-1.78%'. 지시해도 하락 수치가 많을 때 모델이 자주 풀어 쓰므로 코드가 고친다
var (
	spelledSignedPercent = regexp.MustCompile(`(플러스|마이너스)\s?(\d+(?:\.\d+)?)\s?(?:퍼센트|%)`)
	spelledPercent       = regexp.MustCompile(`(\d+(?:\.\d+)?)\s?퍼센트`)
)

func normalizeNumbers(s string) string {
	s = spelledSignedPercent.ReplaceAllStringFunc(s, func(m string) string {
		g := spelledSignedPercent.FindStringSubmatch(m)
		sign := "+"
		if g[1] == "마이너스" {
			sign = "-"
		}
		return sign + g[2] + "%"
	})
	return spelledPercent.ReplaceAllString(s, "$1%")
}

// normalizeOutput 모델 출력의 숫자 표기를 고친다 (검증 전에)
func normalizeOutput(out insightOutput) insightOutput {
	out.Summary = normalizeNumbers(out.Summary)
	for i := range out.Drivers {
		out.Drivers[i] = normalizeNumbers(out.Drivers[i])
	}
	for i := range out.Risks {
		out.Risks[i] = normalizeNumbers(out.Risks[i])
	}
	return out
}

// insightOutput 모델 출력
type insightOutput struct {
	Summary string   `json:"summary"`
	Drivers []string `json:"drivers"`
	Risks   []string `json:"risks"`
	Sources []string `json:"sources"`
}

// Gemini 재시도: 요청 한도 초과·서버 오류는 잠시 뒤 다시 보낸다
var geminiRetryBackoff = []time.Duration{time.Second, 3 * time.Second}

// generateInsightText 형식·근거 검증을 통과할 때까지 최대 2번 생성한다
func generateInsightText(ctx context.Context, apiKey, model string, p insightPrompt, system, prompt string, newsIDs map[string]bool) (insightOutput, error) {
	var lastErr error
	for attempt := 1; attempt <= 2; attempt++ {
		text, err := callGemini(ctx, apiKey, model, p, system, prompt)
		if err != nil {
			return insightOutput{}, err
		}
		var out insightOutput
		if err := json.Unmarshal([]byte(text), &out); err != nil {
			lastErr = fmt.Errorf("응답 JSON 파싱 실패: %w", err)
		} else {
			out = normalizeOutput(out)
			if lastErr = validateInsight(out, prompt, newsIDs); lastErr == nil {
				return out, nil
			}
		}
		log.Printf("인사이트 응답 거절(%d/2): %v\n", attempt, lastErr)
	}
	return insightOutput{}, lastErr
}

// validateInsight 형식, 인용한 기사 id, 퍼센트 숫자 일치, 금지 표현을 확인한다
func validateInsight(out insightOutput, prompt string, newsIDs map[string]bool) error {
	if strings.TrimSpace(out.Summary) == "" {
		return errors.New("summary 없음")
	}
	if utf8.RuneCountInString(out.Summary) > 400 {
		return errors.New("summary가 너무 김")
	}
	if len(out.Drivers) == 0 || len(out.Drivers) > 4 || len(out.Risks) > 3 {
		return fmt.Errorf("항목 수 이상 (drivers %d, risks %d)", len(out.Drivers), len(out.Risks))
	}
	for _, id := range out.Sources {
		if !newsIDs[id] {
			return fmt.Errorf("없는 기사 인용: %s", id)
		}
	}
	all := strings.Join(append(append([]string{out.Summary}, out.Drivers...), out.Risks...), " ")
	if m := forbiddenInsight.FindString(all); m != "" {
		return fmt.Errorf("투자 권유·예측 표현: %s", m)
	}
	if m := spelledNumber.FindString(all); m != "" {
		return fmt.Errorf("숫자를 말로 풀어 씀: %s", m)
	}
	allowed := map[string]bool{}
	for _, p := range percentToken.FindAllString(prompt, -1) {
		allowed[p] = true
	}
	for _, p := range percentToken.FindAllString(all, -1) {
		if !allowed[p] {
			return fmt.Errorf("입력에 없는 수치: %s", p)
		}
	}
	if err := checkWordConsistency(all, prompt); err != nil {
		return err
	}
	return nil
}

// 숫자는 맞게 옮겼어도 단어를 틀리는 실수('상승 0종'을 '하락 0종'으로, '-2.84% 상승')를 잡는다

// '상승 0종', '하락 종목 6종' 같은 종목 수 표현
var breadthClaim = regexp.MustCompile(`(상승|하락|보합)\s?(?:종목\s?)?(\d+)\s?종`)

// 입력의 부호 있는 수치 ('-2.84%', '+980,000원')
var signedValue = regexp.MustCompile(`([+-])(\d[\d,]*(?:\.\d+)?)(%p|%|원)`)

// 수치 바로 뒤에 오는 방향 단어 ('2.84% 하락', '+3.59%의 수익', '120,000원 손실')
var directionClaim = regexp.MustCompile(`([+-]?)(\d[\d,]*(?:\.\d+)?)(%p|%|원)\s?(?:의|로|가|이|만큼|가량|정도)?\s?(상승|올랐|올라|오른|반등|급등|증가|늘었|늘어|수익|이익|하락|내렸|내려|내린|떨어|급락|감소|줄었|줄어|손실)`)

var upWords = map[string]bool{"상승": true, "올랐": true, "올라": true, "오른": true, "반등": true, "급등": true, "증가": true, "늘었": true, "늘어": true, "수익": true, "이익": true}

// checkWordConsistency 종목 수 표현이 입력과 같은지, 수치의 방향 단어·부호가 입력의 부호와 맞는지 확인한다
func checkWordConsistency(text, prompt string) error {
	compactPrompt := strings.ReplaceAll(prompt, " ", "")
	for _, m := range breadthClaim.FindAllStringSubmatch(text, -1) {
		if !strings.Contains(compactPrompt, m[1]+m[2]+"종") {
			return fmt.Errorf("입력과 다른 종목 수 표현: %s", m[0])
		}
	}

	// 입력에서 수치별 부호 (같은 값이 +와 -로 모두 나오면 판단하지 않는다)
	signs := map[string]map[string]bool{}
	for _, m := range signedValue.FindAllStringSubmatch(prompt, -1) {
		if m[3] == "%p" {
			continue
		}
		key := strings.ReplaceAll(m[2], ",", "") + m[3]
		if signs[key] == nil {
			signs[key] = map[string]bool{}
		}
		signs[key][m[1]] = true
	}
	for _, idx := range directionClaim.FindAllStringSubmatchIndex(text, -1) {
		m := make([]string, 5)
		for g := range m {
			if idx[2*g] >= 0 {
				m[g] = text[idx[2*g]:idx[2*g+1]]
			}
		}
		// '상승률'·'수익률'처럼 '률'이 붙으면 방향이 아니라 지표 이름이다
		if m[3] == "%p" || strings.HasPrefix(text[idx[1]:], "률") {
			continue
		}
		seen := signs[strings.ReplaceAll(m[2], ",", "")+m[3]]
		if len(seen) != 1 {
			continue
		}
		actual := "+"
		if seen["-"] {
			actual = "-"
		}
		if m[1] != "" && m[1] != actual {
			return fmt.Errorf("입력과 부호가 다른 수치: %s", m[0])
		}
		if claimedUp := upWords[m[4]]; claimedUp != (actual == "+") {
			return fmt.Errorf("수치와 방향이 맞지 않음: %s", m[0])
		}
	}
	return nil
}

// callGemini generateContent 한 번 호출 (429·5xx·네트워크 오류는 재시도). 모델 출력 텍스트(JSON 문자열)를 돌려준다
func callGemini(ctx context.Context, apiKey, model string, p insightPrompt, system, prompt string) (string, error) {
	body, err := json.Marshal(map[string]any{
		"systemInstruction": map[string]any{"parts": []map[string]string{{"text": system}}},
		"contents":          []map[string]any{{"role": "user", "parts": []map[string]string{{"text": prompt}}}},
		"generationConfig": map[string]any{
			"temperature":      p.Temperature,
			"responseMimeType": "application/json",
			"responseSchema":   p.Schema,
		},
	})
	if err != nil {
		return "", err
	}
	apiURL := "https://generativelanguage.googleapis.com/v1beta/models/" + url.PathEscape(model) + ":generateContent"

	for attempt := 0; ; attempt++ {
		last := attempt >= len(geminiRetryBackoff)
		status, respBody, err := postJSON(ctx, apiURL, apiKey, body)
		switch {
		case err == nil && status == http.StatusOK:
			return extractGeminiText(respBody)
		case err == nil && status != http.StatusTooManyRequests && status < 500:
			return "", fmt.Errorf("Gemini 호출 실패: 상태 코드 %d, 응답: %.300s", status, respBody)
		case last:
			if err != nil {
				return "", fmt.Errorf("Gemini 호출 실패: %w", err)
			}
			return "", fmt.Errorf("Gemini 호출 실패: 상태 코드 %d", status)
		}
		log.Printf("Gemini 일시적 실패(상태 %d, 오류 %v), %s 뒤 재시도\n", status, err, geminiRetryBackoff[attempt])
		select {
		case <-time.After(geminiRetryBackoff[attempt]):
		case <-ctx.Done():
			return "", ctx.Err()
		}
	}
}

func postJSON(ctx context.Context, apiURL, apiKey string, body []byte) (int, []byte, error) {
	reqCtx, cancel := context.WithTimeout(ctx, 60*time.Second)
	defer cancel()
	req, err := http.NewRequestWithContext(reqCtx, http.MethodPost, apiURL, bytes.NewReader(body))
	if err != nil {
		return 0, nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("x-goog-api-key", apiKey) // 키는 URL 쿼리가 아니라 헤더로 (로그에 남지 않게)
	resp, err := httpClient.Do(req)
	if err != nil {
		return 0, nil, err
	}
	defer resp.Body.Close()
	b, err := io.ReadAll(resp.Body)
	return resp.StatusCode, b, err
}

func extractGeminiText(body []byte) (string, error) {
	var res struct {
		Candidates []struct {
			Content struct {
				Parts []struct {
					Text string `json:"text"`
				} `json:"parts"`
			} `json:"content"`
		} `json:"candidates"`
	}
	if err := json.Unmarshal(body, &res); err != nil {
		return "", fmt.Errorf("Gemini 응답 파싱 실패: %w", err)
	}
	if len(res.Candidates) == 0 || len(res.Candidates[0].Content.Parts) == 0 {
		return "", fmt.Errorf("Gemini 응답에 내용 없음: %.300s", body)
	}
	return res.Candidates[0].Content.Parts[0].Text, nil
}

// buildCoinPrompt 코인 하나의 입력. 숫자는 화면 표기와 같은 형식으로 넣는다
func buildCoinPrompt(d coinData, news []NewsItem, now time.Time) string {
	var b strings.Builder
	fmt.Fprintf(&b, "<window>최근 24시간, 기준 시각 %s (KST)</window>\n<data>\n", now.Format("2006-01-02 15:04"))
	fmt.Fprintf(&b, "코인: %s (%s)\n", d.Name, d.Symbol)
	if d.Reason != "" {
		fmt.Fprintf(&b, "분석 대상 선정 이유: %s\n", d.Reason)
	}
	fmt.Fprintf(&b, "현재가: %s\n", formatWon(d.Price))
	fmt.Fprintf(&b, "등락률(전일 종가 대비): %s\n", signedPercent(d.Change))
	fmt.Fprintf(&b, "고가/저가: %s / %s\n", formatWon(d.High), formatWon(d.Low))
	fmt.Fprintf(&b, "24시간 거래대금: %s", formatEok(d.Volume24h))
	if d.VolumeRatio > 0 {
		fmt.Fprintf(&b, " (최근 7일 평균의 %.1f배)", d.VolumeRatio)
	}
	b.WriteString("\n")
	if d.Has7d {
		fmt.Fprintf(&b, "7일 등락률: %s\n", signedPercent(d.Change7d))
	}
	fmt.Fprintf(&b, "유의/주의 지정: %s\n", designation(d.Warning, d.Caution))
	fmt.Fprintf(&b, "분위기 라벨(규칙으로 정함): %s\n</data>\n", d.Mood)
	writeNews(&b, news)
	return b.String()
}

// buildMarketPrompt 시장 전체 입력
func buildMarketPrompt(m marketData, news []NewsItem, now time.Time) string {
	var b strings.Builder
	fmt.Fprintf(&b, "<window>최근 24시간, 기준 시각 %s (KST)</window>\n<data>\n", now.Format("2006-01-02 15:04"))
	fmt.Fprintf(&b, "대상: 원화 마켓 상장 코인 %d종\n", m.Listed)
	fmt.Fprintf(&b, "상승 %d종 / 하락 %d종 / 보합 %d종\n", m.Up, m.Down, m.Listed-m.Up-m.Down)
	fmt.Fprintf(&b, "등락률 중앙값(전일 종가 대비): %s\n", signedPercent(m.MedianChange))
	fmt.Fprintf(&b, "비트코인: %s / 이더리움: %s\n", signedPercent(m.BTC), signedPercent(m.ETH))
	b.WriteString("거래대금 상위 5:\n")
	for _, c := range m.TopVolume {
		fmt.Fprintf(&b, "  - %s(%s): %s, 거래대금 %s\n", c.Name, c.Symbol, signedPercent(c.Change), formatEok(c.Volume24h))
	}
	fmt.Fprintf(&b, "유의 지정 %d종 / 주의 지정 %d종\n", m.Warnings, m.Cautions)
	fmt.Fprintf(&b, "분위기 라벨(규칙으로 정함, 등락률 중앙값 기준): %s\n</data>\n", m.Mood)
	writeNews(&b, news)
	return b.String()
}

func writeNews(b *strings.Builder, news []NewsItem) {
	if len(news) == 0 {
		b.WriteString("<news>관련 기사 없음</news>\n")
		return
	}
	b.WriteString("<news>\n")
	for _, n := range news {
		fmt.Fprintf(b, "[%s] %s | %s | %s\n", n.ID, n.PublishedAt, n.Title, n.Description)
	}
	b.WriteString("</news>\n")
}

func designation(warning, caution bool) string {
	switch {
	case warning && caution:
		return "유의 종목, 투자 주의"
	case warning:
		return "유의 종목"
	case caution:
		return "투자 주의"
	default:
		return "없음"
	}
}

func signedPercent(v float64) string {
	return fmt.Sprintf("%+.2f%%", v)
}

// formatWon 100원 이상은 정수 쉼표, 그 아래는 소수 (업비트 호가 단위와 비슷하게)
func formatWon(v float64) string {
	switch {
	case v >= 100:
		return commaInt(int64(math.Round(v))) + "원"
	case v >= 1:
		return fmt.Sprintf("%.2f원", v)
	default:
		return fmt.Sprintf("%.4f원", v)
	}
}

// formatEok 원 → '3,120억원' (1억 미만은 '1억원 미만')
func formatEok(v float64) string {
	eok := int64(math.Round(v / 1e8))
	if eok < 1 {
		return "1억원 미만"
	}
	return commaInt(eok) + "억원"
}

func commaInt(n int64) string {
	s := fmt.Sprintf("%d", n)
	neg := strings.HasPrefix(s, "-")
	s = strings.TrimPrefix(s, "-")
	var out []byte
	for i := range s {
		if i > 0 && (len(s)-i)%3 == 0 {
			out = append(out, ',')
		}
		out = append(out, s[i])
	}
	if neg {
		return "-" + string(out)
	}
	return string(out)
}
