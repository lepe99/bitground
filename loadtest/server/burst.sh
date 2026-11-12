#!/bin/bash
# S2 지정가 체결 버스트 (ssm-run.sh로 실행)
#   가상 사용자 N명이 KRW-BTC 매수 지정가를 현재가보다 2% 높게 걸어 둔 상태(DB PENDING)를 만든 뒤,
#   Redis 대기열 표시 키(order_book:epoch)를 지워 "Redis 유실 → DB 기준 재구성" 경로로 N건을 한꺼번에 대기열에 올린다.
#   다음 시세가 오면 N건이 동시에 체결 조건을 만족한다 → 체결 큐(Redis Stream)·체결 트랜잭션·아웃박스·알림이 몰리는 상황.
#   측정: 재구성 시각 ~ 첫 체결 ~ 마지막 체결, 초당 체결 수, 누락·중복, 알림 수, 데드락
set -euo pipefail
N=${N:-1000}; TAG=${TAG:-burst$N}
WORK=/root/loadtest
q() { docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N bitground'; }
r() { docker compose exec -T redis redis-cli --no-auth-warning "$@"; }

PRICE=$(curl -s 'https://api.upbit.com/v1/ticker?markets=KRW-BTC' | python3 -c 'import sys,json; print(json.load(sys.stdin)[0]["trade_price"])')
LIMIT=$(python3 -c "print(int(float('$PRICE') * 1.02))")
SEASON=$(echo "SELECT id FROM seasons WHERE status='PENDING' ORDER BY id DESC LIMIT 1;" | q)
echo "BTC $PRICE → limit $LIMIT, season $SEASON, N=$N"
DL0=$(echo "SELECT COUNT FROM information_schema.INNODB_METRICS WHERE NAME='lock_deadlocks';" | q)
MAXID=$(echo "SELECT COALESCE(MAX(id),0) FROM orders;" | q)
NOTI0=$(echo "SELECT COALESCE(MAX(id),0) FROM notifications;" | q)

# 사용자 한 명당 주문 1건 (서로 다른 사용자가 동시에 체결 → 사용자별 잠금은 겹치지 않고, 보유 자산 행 생성이 몰린다)
q <<EOF
INSERT INTO orders (user_id, symbol_id, season_id, order_type, status, reserve_price, amount, fee)
SELECT u.id, (SELECT id FROM coins WHERE symbol = 'KRW-BTC'), $SEASON, 'BUY', 'PENDING', $LIMIT, 0.0001, 0
FROM users u WHERE u.provider = 'loadtest' ORDER BY u.id LIMIT $N;
EOF
echo "inserted: $(echo "SELECT COUNT(*) FROM orders WHERE id > $MAXID;" | q)"

T0=$(date +%s.%N)
r DEL order_book:epoch > /dev/null
echo "trigger $(date '+%T.%3N') (epoch 키 삭제 → 10초 안에 재구성)"

for i in $(seq 1 180); do
  sleep 1
  done_n=$(echo "SELECT COUNT(*) FROM orders WHERE id > $MAXID AND status = 'COMPLETED';" | q)
  [ $((i % 5)) -eq 0 ] && echo "  +${i}s completed=$done_n"
  [ "$done_n" -ge "$N" ] && break
done

echo "== 결과"
q <<EOF
SELECT 'completed', COUNT(*) FROM orders WHERE id > $MAXID AND status = 'COMPLETED';
SELECT 'pending', COUNT(*) FROM orders WHERE id > $MAXID AND status = 'PENDING';
SELECT 'first_exec', DATE_FORMAT(MIN(updated_at), '%T.%f'), 'last_exec', DATE_FORMAT(MAX(updated_at), '%T.%f'),
       'span_ms', TIMESTAMPDIFF(MICROSECOND, MIN(updated_at), MAX(updated_at)) / 1000
  FROM orders WHERE id > $MAXID AND status = 'COMPLETED';
SELECT 'exec_per_sec', ROUND(COUNT(*) / GREATEST(TIMESTAMPDIFF(MICROSECOND, MIN(updated_at), MAX(updated_at)) / 1e6, 0.001), 1)
  FROM orders WHERE id > $MAXID AND status = 'COMPLETED';
SELECT 'trade_price_distinct', COUNT(DISTINCT trade_price), 'min', MIN(trade_price), 'max', MAX(trade_price) FROM orders WHERE id > $MAXID;
SELECT 'notifications_new', COUNT(*), 'distinct_users', COUNT(DISTINCT user_id) FROM notifications WHERE id > $NOTI0;
SELECT 'outbox_unpublished', COUNT(*) FROM outbox_events WHERE published_at IS NULL;
SELECT 'deadlocks_delta', COUNT - $DL0 FROM information_schema.INNODB_METRICS WHERE NAME = 'lock_deadlocks';
EOF
python3 -c "import time; print('trigger_to_now_s', round(time.time() - $T0, 1))"
echo "== 백엔드 로그: 재구성·오류"
docker logs bitground-backend-1 --since 4m 2>&1 | grep -E 'rebuilt from DB|Deadlock|"level":"ERROR"' | cut -c1-300 | tail -5
echo "== 체결 스트림"
for k in $(r --scan --pattern '*exec*'); do echo "$k xlen=$(r XLEN "$k") pending=$(r XPENDING "$k" order-executors 2>/dev/null | head -1)"; done
