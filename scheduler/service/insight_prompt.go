package service

import (
	"embed"
	"encoding/json"
	"fmt"
	"strings"
)

// 버전이 붙은 인사이트 프롬프트 묶음 (prompts/{버전}/, 바이너리에 포함)
//   - coin_system.txt: 코인 하나를 정리할 때의 규칙
//   - market_system.txt: 시장 전체를 정리할 때의 규칙
//   - generation.json: 응답 스키마 (temperature는 Gemini 3 계열이 무시해 두지 않는다)
//
// 프롬프트를 고칠 때는 기존 폴더를 고치지 않고 새 버전 폴더를 만든 뒤 INSIGHT_PROMPT_VERSION(기본값은 DefaultInsightPromptVersion)만 바꾼다.
// 저장된 행에 버전이 남으므로 같은 평가셋으로 이전 버전과 나란히 비교하고, 문제가 있으면 설정만 되돌린다.
// 코인·시장 데이터(<data>, <news>)는 코드가 만든다.

// DefaultInsightPromptVersion 운영 기본 프롬프트 버전
const DefaultInsightPromptVersion = "daily-insight-v4"

//go:embed prompts
var promptFS embed.FS

type insightPrompt struct {
	Version      string
	CoinSystem   string
	MarketSystem string
	Schema       map[string]any
}

func loadInsightPrompt(version string) (insightPrompt, error) {
	if version == "" {
		version = DefaultInsightPromptVersion
	}
	read := func(name string) (string, error) {
		b, err := promptFS.ReadFile("prompts/" + version + "/" + name)
		if err != nil {
			return "", fmt.Errorf("프롬프트 파일이 없습니다(%s/%s): %w", version, name, err)
		}
		return strings.TrimRight(string(b), "\n"), nil
	}

	p := insightPrompt{Version: version}
	var err error
	if p.CoinSystem, err = read("coin_system.txt"); err != nil {
		return insightPrompt{}, err
	}
	if p.MarketSystem, err = read("market_system.txt"); err != nil {
		return insightPrompt{}, err
	}
	gen, err := read("generation.json")
	if err != nil {
		return insightPrompt{}, err
	}
	var g struct {
		ResponseSchema map[string]any `json:"responseSchema"`
	}
	if err := json.Unmarshal([]byte(gen), &g); err != nil {
		return insightPrompt{}, fmt.Errorf("generation.json 해석 실패(%s): %w", version, err)
	}
	p.Schema = g.ResponseSchema
	return p, nil
}
