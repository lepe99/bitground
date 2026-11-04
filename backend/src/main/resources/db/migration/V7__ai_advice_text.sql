-- AI 시즌 리뷰 본문: 모델 출력이 2000자를 넘으면 저장 오류가 나므로 TEXT로 늘린다
ALTER TABLE `ai_advices` MODIFY COLUMN `advice` text COLLATE utf8mb4_unicode_ci NOT NULL;
