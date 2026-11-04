package config

import (
	"errors"
	"fmt"
	"os"
	"strings"
	"time"
	_ "time/tzdata" // Lambda 런타임에는 tz 데이터가 없으므로 바이너리에 포함
)

// Config 스케줄러 실행 설정 (환경변수에서 읽음)
type Config struct {
	DB DBConfig

	// Mode dev: 6시간 주기 시즌/스플릿, prod: 매월 1·16일 시즌, 8·23일 스플릿
	Mode string
	// TestTime 기준 시각 고정(테스트용). 비어 있거나 "0000-00-00 00:00:00"이면 현재 시각 사용
	TestTime string
	// Location 시즌·일일 작업을 판정하는 기준 시간대
	Location *time.Location

	GeminiAPIKey string
	GeminiModel  string
	// 네이버 뉴스 검색(NAVER API HUB) 키. 비우면 인사이트를 뉴스 없이 시세만으로 만든다
	NaverClientID     string
	NaverClientSecret string
	// InsightPromptVersion 인사이트 프롬프트 버전 (service/prompts/{버전}/). 비우면 기본값
	InsightPromptVersion string

	SeasonName string

	// BackendURL 시즌·스플릿 완료를 알릴 백엔드 주소 (예: https://api.bitground.kr)
	BackendURL string
	// SchedulerKey 백엔드 내부 API 호출용 공유 키
	SchedulerKey string
}

// Load 환경변수에서 설정을 읽고 필수값을 검증합니다.
func Load() (Config, error) {
	cfg := Config{
		DB: DBConfig{
			Host:     os.Getenv("DB_HOST"),
			User:     os.Getenv("DB_USER"),
			Password: os.Getenv("DB_PASSWORD"),
			DBName:   os.Getenv("DB_NAME"),
			Charset:  "utf8mb4",
		},
		Mode:         getEnv("TYPE", "prod"),
		TestTime:     os.Getenv("TEST_TIME"),
		GeminiAPIKey: os.Getenv("GEMINI_API_KEY"),
		GeminiModel:  getEnv("GEMINI_MODEL", "gemini-3.5-flash-lite"),
		// 백엔드와 같은 이름
		NaverClientID:        os.Getenv("NAVER_NEWS_CLIENT_ID"),
		NaverClientSecret:    os.Getenv("NAVER_NEWS_CLIENT_SECRET"),
		InsightPromptVersion: os.Getenv("INSIGHT_PROMPT_VERSION"),
		SeasonName:           os.Getenv("SEASON_NAME"),
		BackendURL:           strings.TrimRight(os.Getenv("BACKEND_URL"), "/"),
		SchedulerKey:         os.Getenv("SEASON_UPDATE_KEY"),
	}

	loc, err := time.LoadLocation(getEnv("TIMEZONE", "Asia/Seoul"))
	if err != nil {
		return Config{}, fmt.Errorf("TIMEZONE 해석 실패: %w", err)
	}
	cfg.Location = loc

	var missing []string
	for name, value := range map[string]string{
		"DB_HOST":           cfg.DB.Host,
		"DB_USER":           cfg.DB.User,
		"DB_NAME":           cfg.DB.DBName,
		"SEASON_NAME":       cfg.SeasonName,
		"BACKEND_URL":       cfg.BackendURL,
		"SEASON_UPDATE_KEY": cfg.SchedulerKey,
	} {
		if value == "" {
			missing = append(missing, name)
		}
	}
	if len(missing) > 0 {
		return Config{}, fmt.Errorf("필수 환경변수가 없습니다: %s", strings.Join(missing, ", "))
	}
	if cfg.Mode != "dev" && cfg.Mode != "prod" {
		return Config{}, errors.New("TYPE은 dev 또는 prod여야 합니다")
	}

	return cfg, nil
}

// Now 이번 실행의 기준 시각 (정시로 반올림)
func (c Config) Now() (time.Time, error) {
	if c.TestTime == "" || c.TestTime == "0000-00-00 00:00:00" {
		return time.Now().In(c.Location).Round(time.Hour), nil
	}
	t, err := time.ParseInLocation("2006-01-02 15:04:05", c.TestTime, c.Location)
	if err != nil {
		return time.Time{}, fmt.Errorf("TEST_TIME 파싱 실패: %w", err)
	}
	return t.Round(time.Hour), nil
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
