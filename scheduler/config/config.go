package config

import (
	"errors"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
	_ "time/tzdata" // Lambda 런타임에는 tz 데이터가 없으므로 바이너리에 포함
)

// Config 스케줄러 실행 설정 (환경변수에서 읽음)
type Config struct {
	DB DBConfig

	// Mode 새 시즌의 기본 일정. dev: 6시간 시즌(3시간째 스플릿), prod: 매월 1·16일 시작(7일 뒤 스플릿).
	// 정산·스플릿 시각은 DB의 시즌 일정이 기준이다 (service.SeasonSchedule)
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
	// InsightEveryHour 로컬 테스트용: 0시만이 아니라 매 실행마다 인사이트를 만든다 (INSIGHT_EVERY_HOUR=1, Gemini 호출 약 10회/시간)
	InsightEveryHour bool

	// InsightPromptVersion 인사이트 프롬프트 버전 (service/prompts/{버전}/). 비우면 기본값
	InsightPromptVersion string

	// 뉴스 아카이브(Pinecone). PINECONE_API_KEY가 없으면 수집하지 않는다
	PineconeAPIKey     string
	PineconeIndex      string
	NewsEmbedModel     string
	NewsEmbedDimension int
	NewsEmbedPerMinute int

	SeasonName string

	// CoinGeckoAPIKey 마켓 인덱스용 무료 Demo 키 (COINGECKO_DEMO_API_KEY). 비우면 키 없이 호출
	CoinGeckoAPIKey string

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
		InsightEveryHour:     os.Getenv("INSIGHT_EVERY_HOUR") == "1",
		PineconeAPIKey:       os.Getenv("PINECONE_API_KEY"),
		PineconeIndex:        getEnv("PINECONE_INDEX", "bitground-news"),
		NewsEmbedModel:       getEnv("NEWS_EMBED_MODEL", "gemini-embedding-2"),
		SeasonName:           os.Getenv("SEASON_NAME"),
		CoinGeckoAPIKey:      os.Getenv("COINGECKO_DEMO_API_KEY"),
		BackendURL:           strings.TrimRight(os.Getenv("BACKEND_URL"), "/"),
		SchedulerKey:         os.Getenv("SEASON_UPDATE_KEY"),
	}

	loc, err := time.LoadLocation(getEnv("TIMEZONE", "Asia/Seoul"))
	if err != nil {
		return Config{}, fmt.Errorf("TIMEZONE 해석 실패: %w", err)
	}
	cfg.Location = loc

	// Pinecone 인덱스 차원과 같아야 한다 (gemini-embedding-2 기본 3072, 768로 줄여 저장)
	cfg.NewsEmbedDimension = 768
	if v := os.Getenv("NEWS_EMBED_DIM"); v != "" {
		dim, err := strconv.Atoi(v)
		if err != nil || dim <= 0 {
			return Config{}, fmt.Errorf("NEWS_EMBED_DIM이 올바르지 않습니다: %q", v)
		}
		cfg.NewsEmbedDimension = dim
	}
	// 분당 임베딩 요청 상한 (Gemini 요청 한도에 맞춘다)
	if v := os.Getenv("NEWS_EMBED_RPM"); v != "" {
		rpm, err := strconv.Atoi(v)
		if err != nil || rpm <= 0 {
			return Config{}, fmt.Errorf("NEWS_EMBED_RPM이 올바르지 않습니다: %q", v)
		}
		cfg.NewsEmbedPerMinute = rpm
	}

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
