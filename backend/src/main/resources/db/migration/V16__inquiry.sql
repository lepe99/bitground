-- 문의: 커뮤니티 게시글의 비공개 분류. 작성자와 관리자만 보고, 관리자가 댓글로 답한다
ALTER TABLE posts
    MODIFY category ENUM ('CHAT', 'INFO', 'QUESTION', 'INQUIRY') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    -- 문의 유형 (문의 글만): 버그 제보, 계정·이용, 건의, 기타
    ADD COLUMN inquiry_type ENUM ('BUG', 'ACCOUNT', 'SUGGESTION', 'ETC') CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL AFTER category,
    -- 관리자가 처음 답변한 시각 (비어 있으면 답변 대기)
    ADD COLUMN answered_at DATETIME(6) NULL AFTER inquiry_type,
    -- 내 문의 목록 (작성자별)
    ADD INDEX idx_posts_category_user_created (category, user_id, created_at DESC);

-- 새 문의·추가 질문 알림 (관리자에게)
ALTER TABLE notifications
    MODIFY message_type ENUM ('NOTICE', 'ORDER_EXECUTION', 'SEASON_UPDATE', 'COMMENT', 'INQUIRY')
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;
