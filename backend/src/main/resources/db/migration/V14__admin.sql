-- 관리자 페이지: 사용자 이용 정지, 관리자 작업 기록
ALTER TABLE users
    ADD COLUMN suspended_at DATETIME(6) NULL COMMENT '이용 정지 시각 (NULL이면 정상)',
    ADD COLUMN suspended_reason VARCHAR(200) NULL COMMENT '이용 정지 사유 (관리자만 봄)';

-- 누가 언제 무엇을 했는지 (정지·해제, 게시글·댓글 삭제, 공지 등록·수정·삭제)
CREATE TABLE admin_audit_logs (
    id          INT          NOT NULL AUTO_INCREMENT,
    admin_id    INT          NOT NULL,
    action      VARCHAR(40)  NOT NULL,
    target_type VARCHAR(20)  NOT NULL,
    target_id   INT          NULL,
    detail      VARCHAR(500) NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_admin_audit_logs_created_at (created_at DESC),
    CONSTRAINT admin_audit_logs_users_id_fk FOREIGN KEY (admin_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
