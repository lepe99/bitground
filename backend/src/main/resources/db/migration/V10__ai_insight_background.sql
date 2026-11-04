-- 일일 AI 인사이트 배경 (daily-insight-v4): 뉴스 아카이브(Pinecone)에서 찾은 최근 30일 관련 기사로 쓴 지난 흐름 1~2문장.
-- 오늘 움직임과 인과로 잇지 않으며, 인용한 배경 기사는 sources에 함께 들어간다. 배경 기사가 없으면 NULL
ALTER TABLE `ai_insights`
    ADD COLUMN `background` text COLLATE utf8mb4_unicode_ci DEFAULT NULL AFTER `sources`;
