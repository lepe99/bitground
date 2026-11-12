#!/bin/bash
# 운영 앱 서버에서 부하 테스트 준비 (ssm-run.sh로 실행, 작업 폴더 /opt/bitground/deploy/app)
#   1) MySQL 백업 → S3   2) 스케줄러 수집 데이터 행 수 기록   3) 가상 사용자 1500명(provider='loadtest')
#   4) 가상 사용자 토큰(서버 안에서 만들고 S3 백업 버킷의 mysql/loadtest/로만 전달)   5) 요청 횟수 제한 끄기
set -euo pipefail
N=${N:-1500}
WORK=/root/loadtest; mkdir -p "$WORK"; chmod 700 "$WORK"
BUCKET=$(grep '^BACKUP_BUCKET=' .env | cut -d= -f2)
q() { docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N bitground'; }

echo "== 1. 백업"
BACKUP_BUCKET=$BUCKET ./backup.sh

echo "== 2. 스케줄러 수집 데이터 기준 행 수 (정리 후 같은지 비교)"
date '+%F %T' > "$WORK/start.txt"
echo "SELECT 'coins', COUNT(*) FROM coins UNION ALL SELECT 'coin_price_history', COUNT(*) FROM coin_price_history
 UNION ALL SELECT 'market_indices', COUNT(*) FROM market_indices UNION ALL SELECT 'ai_insights', COUNT(*) FROM ai_insights
 UNION ALL SELECT 'seasons', COUNT(*) FROM seasons UNION ALL SELECT 'real_users', COUNT(*) FROM users WHERE provider <> 'loadtest';" \
  | q | tee "$WORK/baseline.txt"

echo "== 3. 가상 사용자"
have=$(echo "SELECT COUNT(*) FROM users WHERE provider='loadtest';" | q)
if [ "$have" -lt "$N" ]; then
  python3 - "$have" "$N" <<'EOF' | q
import sys
a, n = int(sys.argv[1]), int(sys.argv[2])
rows = ",".join(f"(10000000,'loadtest','lt-{i}','lt{i}',1)" for i in range(a + 1, n + 1))
print(f"INSERT INTO users (cash, provider, provider_id, name, is_onboarded) VALUES {rows};")
EOF
fi
echo "SELECT COUNT(*), MIN(id), MAX(id) FROM users WHERE provider='loadtest';" | q

echo "== 4. 토큰 (10시간 유효, 값은 출력하지 않음)"
echo "SELECT id, provider_id FROM users WHERE provider='loadtest' ORDER BY id;" | q > "$WORK/users.tsv"
python3 - "$WORK/users.tsv" "$WORK/tokens.json" <<'EOF'
import base64, hashlib, hmac, json, sys, time
secret = next(l.split('=', 1)[1].strip() for l in open('backend.env') if l.startswith('JWT_SECRET='))
b64 = lambda b: base64.urlsafe_b64encode(b).rstrip(b'=').decode()
now = int(time.time()); header = b64(json.dumps({'alg': 'HS512'}).encode()); out = []
for line in open(sys.argv[1]):
    uid, pid = line.split()
    payload = b64(json.dumps({'sub': pid, 'provider': 'loadtest', 'auth': 'ROLE_USER', 'userId': int(uid), 'iat': now,
                              'iss': 'bitground-auth', 'aud': 'bitground-api', 'exp': now + 10 * 3600}).encode())
    sig = hmac.new(secret.encode(), f'{header}.{payload}'.encode(), hashlib.sha512).digest()
    out.append(f'{header}.{payload}.{b64(sig)}')
json.dump(out, open(sys.argv[2], 'w')); print('tokens', len(out))
EOF
chmod 600 "$WORK/tokens.json"
aws s3 cp "$WORK/tokens.json" "s3://$BUCKET/mysql/loadtest/tokens.json" --only-show-errors
echo "s3://$BUCKET/mysql/loadtest/tokens.json"

echo "== 5. 요청 횟수 제한 끄기 (backend.env에 임시로 추가, cleanup.sh가 되돌린다)"
grep -q '^RATELIMIT_ENABLED=' backend.env || echo 'RATELIMIT_ENABLED=false' >> backend.env
docker compose up -d backend
for _ in $(seq 1 36); do
  [ "$(docker inspect -f '{{.State.Health.Status}}' bitground-backend-1)" = healthy ] && break; sleep 5
done
docker inspect -f 'backend {{.State.Health.Status}}' bitground-backend-1
