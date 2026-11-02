package util

import (
	"Bitground-go/model"
	"time"
)

// GeneratePlaceholders IN 절 placeholder 생성 헬퍼 함수
func GeneratePlaceholders(count int) string {
	if count == 0 {
		return ""
	}

	placeholders := "?"
	for i := 1; i < count; i++ {
		placeholders += ",?"
	}
	return placeholders
}

// TimeCheck 기준 시각(정시)과 실행 모드로 이번에 수행할 작업 플래그를 반환합니다.
//   - 매일 0시: 코인 정보 갱신, AI 인사이트, 일일 자산 스냅샷
//   - prod: 매월 1·16일 0시 시즌 정산, 8·23일 0시 스플릿
//   - dev: 6시간마다 시즌 정산(0·6·12·18시), 그 사이 3시간 지점에 스플릿
func TimeCheck(now time.Time, mode string) model.UpdateFlags {
	day := now.Day()
	hour := now.Hour()

	var flags model.UpdateFlags
	if mode == "dev" {
		flags.Season = hour%6 == 0
		flags.Split = hour%6 == 3
	} else if hour == 0 {
		flags.Season = day == 1 || day == 16
		flags.Split = day == 8 || day == 23
	}

	if hour == 0 {
		flags.Insight = true
		flags.Coin = true
	}

	return flags
}
