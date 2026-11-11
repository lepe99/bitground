-- AI 시즌 리뷰를 만든 프롬프트 버전과 모델. 기존 리뷰는 마크다운 본문(v1)
-- (season-review-v2부터 advice는 섹션별 JSON, 점수는 코드가 계산)
ALTER TABLE `ai_advices`
    ADD COLUMN `prompt_version` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'v1' AFTER `advice`,
    ADD COLUMN `model` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `prompt_version`;
