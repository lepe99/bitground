//go:build eval

// 일일 AI 인사이트 평가셋. 고정 입력(testdata/insight_eval.json)을 실제 Gemini로 여러 번 돌려
// 자동 지표와 사람 평가용 원문을 scheduler/eval-reports에 남긴다. 프롬프트·모델을 바꿀 때마다 같은 입력으로 비교한다.
//
// 일반 테스트에서는 빠진다 (빌드 태그 eval). 실행 (scheduler 폴더에서):
//
//	go test -tags eval -run TestInsightEval -v -timeout 10m ./service
//
// GEMINI_API_KEY·GEMINI_MODEL은 환경변수 또는 scheduler/.env에서 읽고, EVAL_RUNS로 케이스당 반복 횟수(기본 2),
// EVAL_DELAY_MS로 호출 간격(기본 4000ms)을 정한다.
package service

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"testing"
	"time"
	"unicode/utf8"

	"github.com/joho/godotenv"
)

type evalNews struct {
	ID, Title, PublishedAt, Description string
}

type evalCase struct {
	Name          string     `json:"name"`
	Note          string     `json:"note"`
	Kind          string     `json:"kind"` // coin | market
	Coin          coinData   `json:"coin"`
	Market        marketData `json:"market"`
	News          []evalNews `json:"news"`
	ExpectSources string     `json:"expectSources"` // some: 기사를 인용해야 함 / none: 인용하면 안 됨
}

type evalRun struct {
	caseName     string
	run          int
	millis       int64
	err          error
	out          insightOutput
	validation   error
	numbers      int
	matched      int
	unmatched    []string
	countsOk     bool
	moodOk       bool
	sourcesOk    bool
	breadthSlips []string
}

// 본문의 숫자 + 단위 ('3,120억원'은 '억원'으로 먼저 잡는다)
var evalNumber = regexp.MustCompile(`(\d[\d,]*(?:\.\d+)?)(%p|%|억원|원|배|종)`)

// 엑셀이 한글 CSV를 UTF-8로 읽도록 앞에 붙이는 BOM
const csvBOM = string(rune(0xFEFF))

// '상승 0종', '하락 6종' 같은 종목 수 표현 (데이터와 대조)
var evalBreadth = regexp.MustCompile(`(상승|하락|보합)\s?(\d+)\s?종`)

func TestInsightEval(t *testing.T) {
	_ = godotenv.Load("../.env")
	apiKey := os.Getenv("GEMINI_API_KEY")
	if apiKey == "" {
		t.Skip("GEMINI_API_KEY가 없어 평가를 건너뜀")
	}
	model := os.Getenv("GEMINI_MODEL")
	if model == "" {
		model = "gemini-3.5-flash-lite"
	}
	runs, _ := strconv.Atoi(os.Getenv("EVAL_RUNS"))
	if runs <= 0 {
		runs = 2
	}
	// 호출 사이 간격: 연속으로 부르면 요청 한도(429)에 걸려 평가가 아니라 한도를 재게 된다
	delayMs, err := strconv.Atoi(os.Getenv("EVAL_DELAY_MS"))
	if err != nil || delayMs < 0 {
		delayMs = 4000
	}

	raw, err := os.ReadFile("testdata/insight_eval.json")
	if err != nil {
		t.Fatal(err)
	}
	var cases []evalCase
	if err := json.Unmarshal(raw, &cases); err != nil {
		t.Fatal(err)
	}

	now := time.Date(2026, 11, 4, 0, 0, 0, 0, time.FixedZone("KST", 9*3600))
	var results []evalRun
	for _, c := range cases {
		news := make([]NewsItem, len(c.News))
		ids := map[string]bool{}
		for i, n := range c.News {
			news[i] = NewsItem{ID: n.ID, Title: n.Title, PublishedAt: n.PublishedAt, Description: n.Description}
			ids[n.ID] = true
		}
		system, prompt, mood := coinSystemInstruction, "", c.Coin.Mood
		if c.Kind == "market" {
			system, prompt, mood = marketSystemInstruction, buildMarketPrompt(c.Market, news, now), c.Market.Mood
		} else {
			prompt = buildCoinPrompt(c.Coin, news, now)
		}

		for run := 1; run <= runs; run++ {
			if len(results) > 0 {
				time.Sleep(time.Duration(delayMs) * time.Millisecond)
			}
			started := time.Now()
			text, err := callGemini(context.Background(), apiKey, model, system, prompt)
			r := evalRun{caseName: c.Name, run: run, millis: time.Since(started).Milliseconds(), err: err}
			if err == nil {
				if err := json.Unmarshal([]byte(text), &r.out); err != nil {
					r.err = fmt.Errorf("JSON 파싱 실패: %w", err)
				} else {
					// 운영과 같은 순서: 숫자 표기 정리 → 검증
					r.out = normalizeOutput(r.out)
					scoreInsight(&r, c, prompt, mood, ids)
				}
			}
			results = append(results, r)
			t.Logf("%s #%d 완료", c.Name, run)
		}
	}

	if err := os.MkdirAll("../eval-reports", 0o755); err != nil {
		t.Fatal(err)
	}
	stamp := time.Now().Format("20060102-150405")
	report := filepath.Join("..", "eval-reports", "insight-"+stamp+".md")
	if err := os.WriteFile(report, []byte(insightReport(model, runs, cases, results)), 0o644); err != nil {
		t.Fatal(err)
	}
	var sheet strings.Builder
	sheet.WriteString(csvBOM + "case,run,정확성(1-5),유용성(1-5),톤(1-5),메모\n")
	for _, r := range results {
		fmt.Fprintf(&sheet, "%s,%d,,,,\n", r.caseName, r.run)
	}
	_ = os.WriteFile(filepath.Join("..", "eval-reports", "insight-"+stamp+"-human.csv"), []byte(sheet.String()), 0o644)
	abs, _ := filepath.Abs(report)
	t.Logf("보고서: %s", abs)
}

func scoreInsight(r *evalRun, c evalCase, prompt, mood string, ids map[string]bool) {
	r.validation = validateInsight(r.out, prompt, ids)
	all := strings.Join(append(append([]string{r.out.Summary}, r.out.Drivers...), r.out.Risks...), "\n")

	normalized := strings.ReplaceAll(prompt, ",", "")
	for _, m := range evalNumber.FindAllStringSubmatch(all, -1) {
		r.numbers++
		if strings.Contains(normalized, strings.ReplaceAll(m[1], ",", "")+m[2]) {
			r.matched++
		} else {
			r.unmatched = append(r.unmatched, m[0])
		}
	}

	r.countsOk = utf8.RuneCountInString(r.out.Summary) <= 200 &&
		len(r.out.Drivers) >= 2 && len(r.out.Drivers) <= 3 && len(r.out.Risks) >= 1 && len(r.out.Risks) <= 2
	r.moodOk = strings.Contains(all, mood)
	switch c.ExpectSources {
	case "some":
		r.sourcesOk = len(r.out.Sources) > 0
	default:
		r.sourcesOk = len(r.out.Sources) == 0
	}

	if c.Kind == "market" {
		want := map[string]int{"상승": c.Market.Up, "하락": c.Market.Down, "보합": c.Market.Listed - c.Market.Up - c.Market.Down}
		for _, m := range evalBreadth.FindAllStringSubmatch(all, -1) {
			if n, _ := strconv.Atoi(m[2]); n != want[m[1]] {
				r.breadthSlips = append(r.breadthSlips, m[0])
			}
		}
	}
}

func insightReport(model string, runs int, cases []evalCase, results []evalRun) string {
	var ok []evalRun
	for _, r := range results {
		if r.err == nil {
			ok = append(ok, r)
		}
	}
	count := func(f func(evalRun) bool) int {
		n := 0
		for _, r := range ok {
			if f(r) {
				n++
			}
		}
		return n
	}
	pct := func(v, total int) string {
		if total == 0 {
			return "-"
		}
		return fmt.Sprintf("%.0f%% (%d/%d)", float64(v)*100/float64(total), v, total)
	}
	numbers, matched := 0, 0
	var times []int64
	for _, r := range ok {
		numbers += r.numbers
		matched += r.matched
		times = append(times, r.millis)
	}
	sort.Slice(times, func(i, j int) bool { return times[i] < times[j] })

	var b strings.Builder
	b.WriteString("# 일일 AI 인사이트 평가 보고서\n\n")
	fmt.Fprintf(&b, "- 실행: %s\n- 모델: `%s` / 프롬프트: `%s`\n- 케이스 %d개 × %d회 = %d회 호출\n\n",
		time.Now().Format("2006-01-02 15:04:05"), model, insightPromptVersion, len(cases), runs, len(results))
	b.WriteString("## 요약\n\n| 지표 | 값 |\n|---|---|\n")
	fmt.Fprintf(&b, "| 호출 성공 | %s |\n", pct(len(ok), len(results)))
	fmt.Fprintf(&b, "| 첫 시도 검증 통과 | %s |\n", pct(count(func(r evalRun) bool { return r.validation == nil }), len(ok)))
	fmt.Fprintf(&b, "| 숫자 일치율 (%%·원·억원·배·종) | %s |\n", pct(matched, numbers))
	fmt.Fprintf(&b, "| 항목 수·요약 길이 준수 | %s |\n", pct(count(func(r evalRun) bool { return r.countsOk }), len(ok)))
	fmt.Fprintf(&b, "| 분위기 라벨 그대로 언급 | %s |\n", pct(count(func(r evalRun) bool { return r.moodOk }), len(ok)))
	fmt.Fprintf(&b, "| 기사 인용 기대와 일치 | %s |\n", pct(count(func(r evalRun) bool { return r.sourcesOk }), len(ok)))
	fmt.Fprintf(&b, "| 종목 수 표현 오류 (낮을수록 좋음) | %s |\n", pct(count(func(r evalRun) bool { return len(r.breadthSlips) > 0 }), len(ok)))
	if len(times) > 0 {
		fmt.Fprintf(&b, "| 응답 시간 중앙값 / 최대 | %dms / %dms |\n", times[len(times)/2], times[len(times)-1])
	}

	b.WriteString("\n## 케이스별\n\n| 케이스 | 회차 | 검증 | 숫자 | 항목 수 | 라벨 | 인용 | 비고 |\n|---|---|---|---|---|---|---|---|\n")
	ox := func(v bool) string {
		if v {
			return "O"
		}
		return "X"
	}
	for _, r := range results {
		if r.err != nil {
			fmt.Fprintf(&b, "| %s | %d | 호출 실패 | | | | | %s |\n", r.caseName, r.run, strings.ReplaceAll(r.err.Error(), "|", "/"))
			continue
		}
		v := "통과"
		if r.validation != nil {
			v = "거절: " + r.validation.Error()
		}
		var notes []string
		if len(r.unmatched) > 0 {
			notes = append(notes, "입력에 없는 숫자: "+strings.Join(r.unmatched, ", "))
		}
		if len(r.breadthSlips) > 0 {
			notes = append(notes, "종목 수 오류: "+strings.Join(r.breadthSlips, ", "))
		}
		fmt.Fprintf(&b, "| %s | %d | %s | %d/%d | %s | %s | %s | %s |\n", r.caseName, r.run, v, r.matched, r.numbers,
			ox(r.countsOk), ox(r.moodOk), ox(r.sourcesOk), strings.Join(notes, " / "))
	}

	b.WriteString("\n## 원문 (사람 평가용)\n\n정확성·유용성·톤을 1~5점으로 매겨 같은 이름의 `-human.csv`에 적는다.\n")
	for _, c := range cases {
		fmt.Fprintf(&b, "\n### %s\n\n> %s\n", c.Name, c.Note)
		for _, r := range results {
			if r.caseName != c.Name || r.err != nil {
				continue
			}
			out, _ := json.MarshalIndent(r.out, "", "  ")
			fmt.Fprintf(&b, "\n**%d회차**\n\n```json\n%s\n```\n", r.run, out)
		}
	}
	return b.String()
}
