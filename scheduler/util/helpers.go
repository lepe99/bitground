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

// TimeCheck 기준 시각(정시)으로 이번에 수행할 일일 작업 플래그를 반환합니다.
//   - 매일 0시: 코인 정보 갱신, AI 인사이트, 일일 자산 스냅샷, 90일 지난 알림 삭제
//
// 시즌 정산·스플릿은 시각이 아니라 DB의 시즌 일정으로 정한다 (service.SeasonSchedule)
func TimeCheck(now time.Time) model.UpdateFlags {
	var flags model.UpdateFlags
	if now.Hour() == 0 {
		flags.Insight = true
		flags.Coin = true
	}
	return flags
}
