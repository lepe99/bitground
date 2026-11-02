package config

import (
	"strings"
	"testing"
	"time"
)

func setRequiredEnv(t *testing.T) {
	t.Helper()
	for k, v := range map[string]string{
		"DB_HOST": "localhost:3306", "DB_USER": "u", "DB_NAME": "bitground",
		"SEASON_NAME": "정규 시즌", "BACKEND_URL": "https://api.example.com/", "SEASON_UPDATE_KEY": "k",
		"TYPE": "prod", "TEST_TIME": "", "TIMEZONE": "",
	} {
		t.Setenv(k, v)
	}
}

func TestLoad(t *testing.T) {
	setRequiredEnv(t)

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load() error: %v", err)
	}
	if cfg.BackendURL != "https://api.example.com" {
		t.Errorf("BackendURL 끝의 / 는 제거해야 함: %q", cfg.BackendURL)
	}
	if cfg.Location.String() != "Asia/Seoul" {
		t.Errorf("기본 시간대는 Asia/Seoul: %s", cfg.Location)
	}
	if cfg.GeminiModel == "" {
		t.Error("GEMINI_MODEL 기본값이 있어야 함")
	}
}

func TestLoadRequiresEnv(t *testing.T) {
	setRequiredEnv(t)
	t.Setenv("SEASON_UPDATE_KEY", "")
	t.Setenv("BACKEND_URL", "")

	_, err := Load()
	if err == nil || !strings.Contains(err.Error(), "SEASON_UPDATE_KEY") || !strings.Contains(err.Error(), "BACKEND_URL") {
		t.Fatalf("빠진 환경변수를 모두 알려야 함: %v", err)
	}

	setRequiredEnv(t)
	t.Setenv("TYPE", "staging")
	if _, err := Load(); err == nil {
		t.Fatal("TYPE은 dev/prod만 허용")
	}
}

func TestNow(t *testing.T) {
	setRequiredEnv(t)
	t.Setenv("TEST_TIME", "2026-11-15 23:40:00")
	cfg, err := Load()
	if err != nil {
		t.Fatal(err)
	}

	now, err := cfg.Now()
	if err != nil {
		t.Fatal(err)
	}
	// 23:40 → 다음 날 0시로 반올림 (스케줄이 몇 분 일찍 돌아도 0시 작업으로 판정)
	if got := now.Format(time.DateTime); got != "2026-11-16 00:00:00" {
		t.Errorf("Now() = %s", got)
	}
	if _, offset := now.Zone(); offset != 9*60*60 {
		t.Errorf("TEST_TIME은 설정한 시간대(KST)로 해석해야 함: offset %d", offset)
	}

	cfg.TestTime = "0000-00-00 00:00:00"
	if now, _ := cfg.Now(); now.Minute() != 0 || now.Second() != 0 {
		t.Errorf("현재 시각도 정시로 반올림해야 함: %s", now)
	}
}
