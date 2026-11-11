package util

import (
	"Bitground-go/model"
	"testing"
	"time"
)

func TestTimeCheck(t *testing.T) {
	at := func(day, hour int) time.Time {
		return time.Date(2026, 11, day, hour, 0, 0, 0, time.UTC)
	}
	tests := []struct {
		name string
		now  time.Time
		want model.UpdateFlags
	}{
		{"0시: 일일 작업", at(2, 0), model.UpdateFlags{Coin: true, Insight: true}},
		{"1일 0시도 일일 작업만 (시즌 정산은 DB 일정으로)", at(1, 0), model.UpdateFlags{Coin: true, Insight: true}},
		{"그 외 시각: 랭킹만", at(2, 7), model.UpdateFlags{}},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := TimeCheck(tt.now); got != tt.want {
				t.Errorf("TimeCheck() = %+v, want %+v", got, tt.want)
			}
		})
	}
}

func TestGeneratePlaceholders(t *testing.T) {
	for count, want := range map[int]string{0: "", 1: "?", 3: "?,?,?"} {
		if got := GeneratePlaceholders(count); got != want {
			t.Errorf("GeneratePlaceholders(%d) = %q, want %q", count, got, want)
		}
	}
}
