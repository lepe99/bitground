package service

import (
	"strings"
	"testing"
)

func TestPlanDelistings(t *testing.T) {
	coins := func(n int) []coinRow {
		out := make([]coinRow, n)
		for i := range out {
			out[i] = coinRow{id: i + 1, symbol: "KRW-C" + string(rune('A'+i%26)) + string(rune('A'+i/26))}
		}
		return out
	}
	listedAll := func(cs []coinRow) map[string]bool {
		m := map[string]bool{}
		for _, c := range cs {
			m[c.symbol] = true
		}
		return m
	}

	t.Run("목록에서 사라진 코인은 상장 폐지, 다시 나타난 코인은 해제", func(t *testing.T) {
		cs := coins(30)
		listed := listedAll(cs)
		delete(listed, cs[0].symbol)
		cs[1].deleted = true // 표시돼 있지만 다시 목록에 있음
		del, re, err := planDelistings(cs, listed)
		if err != nil {
			t.Fatal(err)
		}
		if len(del) != 1 || del[0].id != cs[0].id {
			t.Errorf("delisted = %v", symbolsOf(del))
		}
		if len(re) != 1 || re[0].id != cs[1].id {
			t.Errorf("relisted = %v", symbolsOf(re))
		}
	})

	t.Run("이미 표시된 코인은 다시 처리하지 않음", func(t *testing.T) {
		cs := coins(30)
		listed := listedAll(cs)
		delete(listed, cs[0].symbol)
		cs[0].deleted = true
		del, re, err := planDelistings(cs, listed)
		if err != nil || len(del) != 0 || len(re) != 0 {
			t.Errorf("del=%v re=%v err=%v", symbolsOf(del), symbolsOf(re), err)
		}
	})

	t.Run("빈 응답이면 아무것도 바꾸지 않음", func(t *testing.T) {
		if _, _, err := planDelistings(coins(30), map[string]bool{}); err == nil {
			t.Error("빈 목록인데 에러가 없음")
		}
	})

	t.Run("한꺼번에 너무 많이 사라지면 업비트 응답 이상으로 보고 멈춤", func(t *testing.T) {
		cs := coins(100)
		listed := listedAll(cs)
		for _, c := range cs[:11] { // 상한 10%(10개) 초과
			delete(listed, c.symbol)
		}
		if _, _, err := planDelistings(cs, listed); err == nil {
			t.Error("11개가 사라졌는데 에러가 없음")
		}
		listed[cs[0].symbol] = true // 10개면 허용
		if del, _, err := planDelistings(cs, listed); err != nil || len(del) != 10 {
			t.Errorf("10개: del=%d err=%v", len(del), err)
		}
	})
}

func TestSellValue(t *testing.T) {
	tests := []struct {
		amount, price string
		want          int64
	}{
		{"0.5000000000", "1234.56780000", 617},  // 617.2839 → 내림
		{"12345.6789123400", "0.01230000", 151}, // 151.85... → 내림
		{"0.0010000000", "103621000.00000000", 103621},
		{"2", "100", 200},
	}
	for _, tt := range tests {
		got, err := sellValue(tt.amount, tt.price)
		if err != nil || got != tt.want {
			t.Errorf("sellValue(%s, %s) = %d, %v; want %d", tt.amount, tt.price, got, err, tt.want)
		}
	}
	if _, err := sellValue("x", "1"); err == nil {
		t.Error("잘못된 수량인데 에러가 없음")
	}
}

func TestLiquidationMessage(t *testing.T) {
	msg := liquidationMessage("니어프로토콜", "0.5000000000", "5070.00000000", 2535)
	want := "상장 폐지로 보유 코인을 정리했어요.\n니어프로토콜 0.5개를 개당 5,070원에 팔아 2,535원을 입금했어요"
	if msg != want {
		t.Errorf("got %q", msg)
	}
	if !strings.Contains(liquidationMessage("시바이누", "1000", "0.01230000", 12), "개당 0.0123원") {
		t.Error("100원 미만 가격은 소수로 보여야 함")
	}
	if got := formatThousands(1234567890); got != "1,234,567,890" {
		t.Errorf("formatThousands = %s", got)
	}
}
