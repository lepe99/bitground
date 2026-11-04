-- 내 게시글에 댓글, 내 댓글에 답글이 달리면 알림. 누르면 그 게시글로 가도록 게시글 id를 함께 저장한다
ALTER TABLE notifications
    MODIFY message_type ENUM('NOTICE', 'ORDER_EXECUTION', 'SEASON_UPDATE', 'COMMENT')
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    ADD COLUMN post_id INT NULL AFTER message_type,
    ADD CONSTRAINT notifications_posts_id_fk FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
