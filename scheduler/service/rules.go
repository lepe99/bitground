package service

import (
	"fmt"
	"strings"
)

// 게임 규칙. 같은 값이 백엔드(season/InitialCashPolicy.java)와
// 프론트(constants/season.js, constants/tiers.js)에도 있다. 세 곳이 같은지는 rules_test.go가 확인한다.
const (
	// 시즌 시작 때 모든 사용자의 현금을 이 금액으로 맞춘다
	seasonStartCash = 10_000_000
	// 스플릿 2 시작 때 추가로 지급하는 현금
	splitCash = 10_000_000
)

// tierCutoffs 4위 이하 참가자 중 상위 비율로 정하는 티어 (위 칸부터 확인, 어디에도 안 들면 1).
// 1위는 7, 2~3위는 6
var tierCutoffs = []struct {
	tier  int
	ratio float64
}{
	{5, 0.10},
	{4, 0.25},
	{3, 0.45},
	{2, 0.70},
}

// tierCaseSQL ranks·participants 열로 티어를 구하는 CASE 식
func tierCaseSQL() string {
	var b strings.Builder
	b.WriteString("CASE\n")
	b.WriteString("\t\t           WHEN ranks = 1 THEN 7\n")
	b.WriteString("\t\t           WHEN ranks <= 3 THEN 6\n")
	for _, c := range tierCutoffs {
		fmt.Fprintf(&b, "\t\t           WHEN (ranks - 3) / GREATEST(participants - 3, 1.0) <= %.2f THEN %d\n", c.ratio, c.tier)
	}
	b.WriteString("\t\t           ELSE 1\n")
	b.WriteString("\t\t       END")
	return b.String()
}
