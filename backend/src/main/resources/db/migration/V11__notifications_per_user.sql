-- 공지·시즌 알림은 그동안 1번 사용자 행 하나에 저장해 모두가 같이 봤다.
-- 1번은 실제 사용자라 그 사람의 주문 체결 알림까지 모두에게 보였고, 읽음 여부도 공유돼 읽지 않은 개수를 셀 수 없었다.
-- 이제 사용자마다 한 행씩 저장하므로, 지난 공지·시즌 알림을 그 알림 이후에 가입해 있던 다른 사용자에게 읽음 상태로 복사한다.
INSERT INTO notifications (user_id, message, message_type, is_read, created_at)
SELECT u.id, n.message, n.message_type, 1, n.created_at
FROM notifications n
JOIN users u ON u.id <> n.user_id AND u.is_deleted = 0 AND u.created_at <= n.created_at
WHERE n.user_id = 1
  AND n.message_type IN ('NOTICE', 'SEASON_UPDATE');
