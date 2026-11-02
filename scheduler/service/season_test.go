package service

import (
	"testing"
	"time"
)

func TestProcessSeasonStrings(t *testing.T) {
	tests := []struct {
		seasonName, ogSeasonName, want string
	}{
		{"데브 시즌", "데브 시즌 3", "데브 시즌 4"}, // 같은 이름이면 번호 +1
		{"데브 시즌", "시즌 1", "데브 시즌 1"},    // 이름이 바뀌면 1부터
		{"정규 시즌", "정규 시즌", "정규 시즌 1"},   // 이전 이름에 번호가 없으면 1
		{"정규 시즌", "초기화 시즌 1", "정규 시즌 1"},
	}
	for _, tt := range tests {
		got, err := processSeasonStrings(tt.seasonName, tt.ogSeasonName)
		if err != nil {
			t.Fatalf("processSeasonStrings(%q, %q) error: %v", tt.seasonName, tt.ogSeasonName, err)
		}
		if got != tt.want {
			t.Errorf("processSeasonStrings(%q, %q) = %q, want %q", tt.seasonName, tt.ogSeasonName, got, tt.want)
		}
	}

	if _, err := processSeasonStrings("이름 없음", "시즌 1"); err == nil {
		t.Error("'시즌'이 없는 이름은 에러여야 함")
	}
}

func TestSeasonPeriod(t *testing.T) {
	kst := time.FixedZone("KST", 9*60*60)
	date := func(y int, m time.Month, d int) time.Time { return time.Date(y, m, d, 0, 0, 0, 0, kst) }

	tests := []struct {
		name     string
		now      time.Time
		mode     string
		wantEnd  time.Time
		wantFail bool
	}{
		{"1일 시작은 15일 종료", date(2026, 11, 1), "prod", date(2026, 11, 15), false},
		{"16일 시작은 말일 종료 (30일)", date(2026, 11, 16), "prod", date(2026, 11, 30), false},
		{"16일 시작은 말일 종료 (2월)", date(2026, 2, 16), "prod", date(2026, 2, 28), false},
		{"12월 16일은 해를 넘기지 않음", date(2026, 12, 16), "prod", date(2026, 12, 31), false},
		{"dev는 당일 종료", date(2026, 11, 7), "dev", date(2026, 11, 7), false},
		{"prod 다른 날짜는 에러", date(2026, 11, 8), "prod", time.Time{}, true},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			start, end, err := seasonPeriod(tt.now, tt.mode)
			if tt.wantFail {
				if err == nil {
					t.Fatal("에러가 나야 함")
				}
				return
			}
			if err != nil {
				t.Fatalf("seasonPeriod() error: %v", err)
			}
			if !start.Equal(tt.now) || !end.Equal(tt.wantEnd) {
				t.Errorf("seasonPeriod() = %s ~ %s, want %s ~ %s",
					start.Format(time.DateOnly), end.Format(time.DateOnly),
					tt.now.Format(time.DateOnly), tt.wantEnd.Format(time.DateOnly))
			}
		})
	}
}

func TestRunPeriod(t *testing.T) {
	now := time.Date(2026, 11, 16, 0, 0, 0, 0, time.UTC)
	if got := runPeriod(now); got != "2026-11-16T00" {
		t.Errorf("runPeriod() = %q", got)
	}
	// 같은 시각에 다시 실행(재시도)하면 같은 키 → 중복 정산 방지
	if runPeriod(now) != runPeriod(now.Add(0)) {
		t.Error("같은 기준 시각은 같은 키여야 함")
	}
}

func TestPricesJSON(t *testing.T) {
	got, err := pricesJSON(map[int]float64{3: 1.5, 1: 150000000})
	if err != nil {
		t.Fatal(err)
	}
	want := `[{"id":1,"price":150000000},{"id":3,"price":1.5}]`
	if got != want {
		t.Errorf("pricesJSON() = %s, want %s", got, want)
	}

	empty, _ := pricesJSON(nil)
	if empty != "[]" {
		t.Errorf("빈 맵은 [] 이어야 함 (JSON_TABLE이 null을 받지 않도록): %s", empty)
	}
}
