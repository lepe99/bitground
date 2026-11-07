-- 알림 설정: 종류별 받기(끄면 저장도 실시간 전송도 하지 않음)와 실시간 토스트 표시. 행이 없으면 모두 켬
CREATE TABLE notification_settings
(
    user_id         INT        NOT NULL PRIMARY KEY,
    order_execution TINYINT(1) NOT NULL DEFAULT 1,
    comment         TINYINT(1) NOT NULL DEFAULT 1,
    notice          TINYINT(1) NOT NULL DEFAULT 1,
    season_update   TINYINT(1) NOT NULL DEFAULT 1,
    toast           TINYINT(1) NOT NULL DEFAULT 1,
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT notification_settings_users_id_fk FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 보관 기간(90일)이 지난 알림을 매일 지울 때 날짜로 찾는다 (스케줄러)
ALTER TABLE notifications
    ADD INDEX idx_notifications_created_at (created_at);
