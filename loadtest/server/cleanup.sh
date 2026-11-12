#!/bin/bash
# 부하 테스트 정리 (ssm-run.sh로 실행). provider='loadtest' 사용자와 그 사용자에게 딸린 행만 지운다.
# 스케줄러 수집 데이터(coins, 가격 히스토리, 시장 지수, AI 인사이트)와 실제 사용자 데이터는 건드리지 않는다.
set -euo pipefail
WORK=/root/loadtest
BUCKET=$(grep '^BACKUP_BUCKET=' .env | cut -d= -f2)
q() { docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N bitground'; }
START=$(cat "$WORK/start.txt")

echo "== 1. 테스트 데이터 삭제 (테스트 시작: $START)"
q <<EOF
CREATE TEMPORARY TABLE lt_users AS SELECT id FROM users WHERE provider = 'loadtest';
CREATE TEMPORARY TABLE lt_posts AS SELECT id FROM posts WHERE user_id IN (SELECT id FROM lt_users);
START TRANSACTION;
DELETE FROM notifications WHERE user_id IN (SELECT id FROM lt_users) OR post_id IN (SELECT id FROM lt_posts);
DELETE FROM reactions WHERE user_id IN (SELECT id FROM lt_users);
DELETE FROM comments WHERE post_id IN (SELECT id FROM lt_posts) AND parent_id IS NOT NULL;
DELETE FROM comments WHERE post_id IN (SELECT id FROM lt_posts) OR user_id IN (SELECT id FROM lt_users);
DELETE FROM posts WHERE id IN (SELECT id FROM lt_posts);
DELETE FROM orders WHERE user_id IN (SELECT id FROM lt_users);
DELETE FROM user_assets WHERE user_id IN (SELECT id FROM lt_users);
DELETE FROM user_rankings WHERE user_id IN (SELECT id FROM lt_users);
DELETE FROM user_daily_balances WHERE user_id IN (SELECT id FROM lt_users);
DELETE FROM favorite_coins WHERE user_id IN (SELECT id FROM lt_users);
DELETE FROM notification_settings WHERE user_id IN (SELECT id FROM lt_users);
DELETE FROM ai_advices WHERE user_id IN (SELECT id FROM lt_users);
-- 아웃박스는 사용자 FK가 없다: 테스트 시간 동안 생긴, 이미 전달된 이벤트만 (실제 사용자는 그 시간에 거래하지 않음)
DELETE FROM outbox_events WHERE created_at >= '$START' AND published_at IS NOT NULL;
DELETE FROM users WHERE id IN (SELECT id FROM lt_users);
COMMIT;
SELECT 'loadtest users left', COUNT(*) FROM users WHERE provider = 'loadtest';
SELECT 'orders left', COUNT(*) FROM orders o JOIN users u ON u.id = o.user_id WHERE u.provider = 'loadtest';
EOF

echo "== 2. 기준 행 수와 비교 (coin_price_history·market_indices는 정시마다 늘어나는 게 정상)"
cat "$WORK/baseline.txt"
echo "--- 지금"
echo "SELECT 'coins', COUNT(*) FROM coins UNION ALL SELECT 'coin_price_history', COUNT(*) FROM coin_price_history
 UNION ALL SELECT 'market_indices', COUNT(*) FROM market_indices UNION ALL SELECT 'ai_insights', COUNT(*) FROM ai_insights
 UNION ALL SELECT 'seasons', COUNT(*) FROM seasons UNION ALL SELECT 'real_users', COUNT(*) FROM users WHERE provider <> 'loadtest';" | q

echo "== 3. Redis: 테스트 주문 대기열·랭킹 캐시 비우기"
docker compose exec -T redis sh -c 'redis-cli --no-auth-warning ${REDIS_PASSWORD:+-a "$REDIS_PASSWORD"} --scan --pattern "cache:*" | xargs -r redis-cli --no-auth-warning ${REDIS_PASSWORD:+-a "$REDIS_PASSWORD"} del' || true
# 예약 대기열은 백엔드 재시작 때 DB 기준으로 다시 만들어진다 (아래 4번)

echo "== 4. 요청 횟수 제한 되돌리기 + 백엔드 재생성"
sed -i '/^RATELIMIT_ENABLED=/d' backend.env
docker compose up -d backend
for _ in $(seq 1 36); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' bitground-backend-1)" = healthy ] && break; sleep 5
done
docker inspect -f 'backend {{.State.Health.Status}}' bitground-backend-1
grep -c '^RATELIMIT_ENABLED=' backend.env || echo "RATELIMIT_ENABLED 없음 (기본값 true)"

echo "== 5. 토큰 파일 삭제"
aws s3 rm "s3://$BUCKET/mysql/loadtest/tokens.json" --only-show-errors || true
rm -f "$WORK/tokens.json" "$WORK/users.tsv"
ls -la "$WORK"
