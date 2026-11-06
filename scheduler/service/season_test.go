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

func TestNextSeasonSchedule(t *testing.T) {
	kst := time.FixedZone("KST", 9*60*60)
	at := func(m time.Month, d, h int) time.Time { return time.Date(2026, m, d, h, 0, 0, 0, kst) }
	tests := []struct {
		name                                    string
		base                                    time.Time
		mode                                    string
		wantStart, wantEnd, wantSplit, wantEnds time.Time
	}{
		{"1일 시작: 8일 스플릿, 16일 종료", at(11, 1, 0), "prod", at(11, 1, 0), at(11, 15, 0), at(11, 8, 0), at(11, 16, 0)},
		{"16일 시작: 23일 스플릿, 다음 달 1일 종료", at(11, 16, 0), "prod", at(11, 16, 0), at(11, 30, 0), at(11, 23, 0), at(12, 1, 0)},
		{"12월 16일: 해를 넘겨 1월 1일 종료", at(12, 16, 0), "prod", at(12, 16, 0), at(12, 31, 0), at(12, 23, 0), time.Date(2027, 1, 1, 0, 0, 0, 0, kst)},
		{"관리자가 종료를 20일로 옮김: 다음 경계(1일)에 다시 맞춤", at(11, 20, 0), "prod", at(11, 20, 0), at(11, 30, 0), at(11, 25, 0), at(12, 1, 0)},
		{"9일 15시 종료: 7일 뒤가 16일 경계 이후라 다음 달 1일", at(11, 9, 15), "prod", at(11, 9, 0), at(11, 30, 0), at(11, 16, 0), at(12, 1, 0)},
		{"10일 시작: 16일까지 7일이 안 남아 다음 달 1일", at(11, 10, 0), "prod", at(11, 10, 0), at(11, 30, 0), at(11, 17, 0), at(12, 1, 0)},
		{"dev: 3시간 뒤 스플릿, 6시간 뒤 종료", at(11, 4, 18), "dev", at(11, 4, 18), at(11, 4, 18), at(11, 4, 21), at(11, 5, 0)},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			start, end, split, ends := nextSeasonSchedule(tt.base, tt.mode)
			if !start.Equal(tt.wantStart) || !end.Equal(tt.wantEnd) || !split.Equal(tt.wantSplit) || !ends.Equal(tt.wantEnds) {
				t.Errorf("nextSeasonSchedule() = %s ~ %s, split %s, ends %s", start, end, split, ends)
			}
			if !split.After(tt.base) || !ends.After(split) {
				t.Errorf("시작 < 스플릿 < 종료 순서가 아님: %s, %s, %s", tt.base, split, ends)
			}
		})
	}
}

func TestSeasonScheduleDue(t *testing.T) {
	at := func(h int) time.Time { return time.Date(2026, 11, 6, h, 0, 0, 0, time.UTC) }
	s := SeasonSchedule{ID: 1, SplitAt: at(15), EndsAt: at(18)}
	tests := []struct {
		name          string
		s             SeasonSchedule
		now           time.Time
		season, split bool
	}{
		{"스플릿 전", s, at(14), false, false},
		{"스플릿 시각", s, at(15), false, true},
		{"스플릿을 놓친 뒤 따라잡기", s, at(16), false, true},
		{"이미 지급한 스플릿", SeasonSchedule{SplitAt: at(15), EndsAt: at(18), SplitDone: true}, at(16), false, false},
		{"종료 시각: 정산만 (스플릿은 생략)", s, at(18), true, false},
		{"종료를 놓친 뒤 따라잡기", s, at(20), true, false},
		{"일정 없는 시즌은 아무것도 안 함", SeasonSchedule{ID: 1}, at(20), false, false},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := tt.s.SeasonDue(tt.now); got != tt.season {
				t.Errorf("SeasonDue() = %v", got)
			}
			if got := tt.s.SplitDue(tt.now); got != tt.split {
				t.Errorf("SplitDue() = %v", got)
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
