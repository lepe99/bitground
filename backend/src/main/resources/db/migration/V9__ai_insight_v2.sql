-- 일일 AI 인사이트 v2 (daily-insight-v2): 스케줄러가 데이터를 모아 넣고, 분위기 라벨은 규칙으로 정한다
--   mood: 강세/약간 강세/중립/약간 약세/약세 (등락률 규칙)
--   drivers·risks: 근거·지켜볼 점 (JSON 문자열 배열)
--   sources: 모델이 인용한 기사 [{id, title, url, publishedAt}] (JSON)
--   pick_reason: 분석 대상으로 고른 이유 (주요 코인, 거래대금 상위 …)
-- 기존 행(v1)은 새 컬럼이 비어 있고 본문(insight)과 점수만 있다
ALTER TABLE `ai_insights`
    ADD COLUMN `mood` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `score`,
    ADD COLUMN `drivers` text COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `insight`,
    ADD COLUMN `risks` text COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `drivers`,
    ADD COLUMN `sources` text COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `risks`,
    ADD COLUMN `pick_reason` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `sources`,
    ADD COLUMN `prompt_version` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'v1' AFTER `pick_reason`,
    ADD COLUMN `model` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `prompt_version`;
