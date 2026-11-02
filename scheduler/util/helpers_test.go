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
		mode string
		want model.UpdateFlags
	}{
		{"prod 1일 0시: 시즌 정산 + 일일 작업", at(1, 0), "prod", model.UpdateFlags{Season: true, Coin: true, Insight: true}},
		{"prod 16일 0시: 시즌 정산", at(16, 0), "prod", model.UpdateFlags{Season: true, Coin: true, Insight: true}},
		{"prod 8일 0시: 스플릿", at(8, 0), "prod", model.UpdateFlags{Split: true, Coin: true, Insight: true}},
		{"prod 23일 0시: 스플릿", at(23, 0), "prod", model.UpdateFlags{Split: true, Coin: true, Insight: true}},
		{"prod 1일 6시: 시즌은 0시에만", at(1, 6), "prod", model.UpdateFlags{}},
		{"prod 평일 0시: 일일 작업만", at(2, 0), "prod", model.UpdateFlags{Coin: true, Insight: true}},
		{"dev 6시: 시즌 정산", at(2, 6), "dev", model.UpdateFlags{Season: true}},
		{"dev 9시: 스플릿", at(2, 9), "dev", model.UpdateFlags{Split: true}},
		{"dev 0시: 시즌 정산 + 일일 작업", at(2, 0), "dev", model.UpdateFlags{Season: true, Coin: true, Insight: true}},
		{"dev 7시: 랭킹만", at(2, 7), "dev", model.UpdateFlags{}},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := TimeCheck(tt.now, tt.mode); got != tt.want {
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
