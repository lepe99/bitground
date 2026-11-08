package service

import (
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"testing"
)

// 같은 게임 규칙이 Go·백엔드·프론트 세 곳에 있다. 한쪽만 바꾸면 화면 안내와 실제 계산이 어긋나므로 값이 같은지 확인한다.
// (스케줄러만 따로 받은 경우처럼 다른 폴더가 없으면 건너뜀)
func TestRulesMatchBackendAndFrontend(t *testing.T) {
	root := filepath.Join("..", "..")
	read := func(rel string) string {
		b, err := os.ReadFile(filepath.Join(root, rel))
		if err != nil {
			t.Skipf("%s 없음: %v", rel, err)
		}
		return string(b)
	}
	number := func(src, name string) int {
		m := regexp.MustCompile(name + `\s*=\s*([\d_]+)`).FindStringSubmatch(src)
		if m == nil {
			t.Fatalf("%s를 찾을 수 없음", name)
		}
		n, _ := strconv.Atoi(strings.ReplaceAll(m[1], "_", ""))
		return n
	}

	java := read("backend/src/main/java/bit/bitgroundspring/season/InitialCashPolicy.java")
	js := read("frontend/src/constants/season.js")
	if got := number(java, "SEASON_START_CASH"); got != seasonStartCash {
		t.Errorf("백엔드 SEASON_START_CASH = %d, Go = %d", got, seasonStartCash)
	}
	if got := number(java, "SPLIT_CASH"); got != splitCash {
		t.Errorf("백엔드 SPLIT_CASH = %d, Go = %d", got, splitCash)
	}
	if got := number(js, "SPLIT_CASH"); got != splitCash { // 프론트는 스플릿 안내 문구에만 쓴다
		t.Errorf("프론트 SPLIT_CASH = %d, Go = %d", got, splitCash)
	}

	// 프론트 TIER_CUTOFFS = {5: 10, 4: 25, ...} (상위 %)
	tiers := read("frontend/src/constants/tiers.js")
	block := regexp.MustCompile(`TIER_CUTOFFS\s*=\s*\{([^}]*)\}`).FindStringSubmatch(tiers)
	if block == nil {
		t.Fatal("TIER_CUTOFFS를 찾을 수 없음")
	}
	front := map[int]int{}
	for _, m := range regexp.MustCompile(`(\d+)\s*:\s*(\d+)`).FindAllStringSubmatch(block[1], -1) {
		tier, _ := strconv.Atoi(m[1])
		pct, _ := strconv.Atoi(m[2])
		front[tier] = pct
	}
	if len(front) != len(tierCutoffs) {
		t.Errorf("프론트 티어 기준 %d개, Go %d개", len(front), len(tierCutoffs))
	}
	for _, c := range tierCutoffs {
		if want := int(c.ratio*100 + 0.5); front[c.tier] != want {
			t.Errorf("티어 %d: 프론트 상위 %d%%, Go 상위 %d%%", c.tier, front[c.tier], want)
		}
	}
}

func TestTierCaseSQL(t *testing.T) {
	sql := tierCaseSQL()
	for _, want := range []string{"WHEN ranks = 1 THEN 7", "WHEN ranks <= 3 THEN 6", "<= 0.10 THEN 5", "<= 0.70 THEN 2", "ELSE 1"} {
		if !strings.Contains(sql, want) {
			t.Errorf("%q 없음:\n%s", want, sql)
		}
	}
}
