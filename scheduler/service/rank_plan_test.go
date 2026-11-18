package service

import (
	"Bitground-go/testsupport"
	"context"
	"strings"
	"testing"
)

// 랭킹 총자산 집계가 가격 표(JSON_TABLE)를 한 번만 만들어 두고 조인하는지 실행 계획으로 확인한다.
// 가격 표를 보유 자산 행마다 다시 펼치면 사용자 5만 명에서 8초 넘게 걸린다 (userTotalsCTE 주석 참고).
// 힌트(NO_MERGE)가 빠지면 계획에 "Materialize CTE prices"가 없고 "Materialize table function"만 남는다. Docker 필요.
func TestRankingPricesMaterializedOnce(t *testing.T) {
	ctx := context.Background()
	db, err := testsupport.StartMySQL(ctx)
	if err != nil {
		t.Fatal(err)
	}
	defer db.Close(ctx)

	prices, err := pricesJSON(map[int]float64{1: 100_000_000, 2: 5_000_000})
	if err != nil {
		t.Fatal(err)
	}
	var plan string
	err = db.DB.QueryRowContext(ctx, `EXPLAIN FORMAT=TREE `+userTotalsCTE+` SELECT user_id, total_value FROM totals`,
		prices, 1).Scan(&plan)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(plan, "Materialize CTE prices") {
		t.Errorf("가격 표를 한 번만 만들지 않는 실행 계획:\n%s", plan)
	}
}
