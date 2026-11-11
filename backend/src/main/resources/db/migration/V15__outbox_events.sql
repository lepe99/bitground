-- 아웃박스: 체결 같은 도메인 이벤트를 원래 작업과 같은 트랜잭션에 저장하고, 전달기(OutboxRelay)가 Redis Stream으로 보낸다.
-- 커밋된 작업의 이벤트는 Redis·서버가 죽어도 사라지지 않는다 (published_at이 비어 있으면 다시 보냄)
CREATE TABLE outbox_events
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type   VARCHAR(40) NOT NULL,
    payload      JSON        NOT NULL,
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6) NULL,
    INDEX idx_outbox_unpublished (published_at, id),
    INDEX idx_outbox_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 같은 이벤트가 두 번 전달돼도 알림은 한 번만 저장 (이벤트 처리 쪽이 INSERT IGNORE)
ALTER TABLE notifications
    ADD COLUMN event_id BIGINT NULL,
    ADD UNIQUE KEY uk_notifications_event (event_id);
