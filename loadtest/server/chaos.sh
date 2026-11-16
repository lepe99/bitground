#!/bin/bash
# 부하 중 장애 주입 (ssm-run.sh로 실행). 결과는 /root/loadtest/chaos.log에도 남긴다
#   TARGET=redis   → Redis 컨테이너 재시작. 예약 대기열 표시 키가 사라져 백엔드가 DB 기준으로 다시 만드는지
#   TARGET=backend → 백엔드 컨테이너 재시작(무중단 교체 없이). readiness까지 걸린 시간
#   TARGET=deploy  → rollout.sh로 무중단 교체
set -euo pipefail
WORK=/root/loadtest; LOG=$WORK/chaos.log
q() { docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N bitground'; }
now() { date '+%T.%3N'; }
say() { echo "$(now) [$TARGET] $*" | tee -a "$LOG"; }

T0=$(date +%s.%N)
say "restart 시작"
case ${TARGET:?redis|backend|deploy} in
  redis)
    pending=$(echo "SELECT COUNT(*) FROM orders WHERE status='PENDING';" | q)
    docker compose restart redis > /dev/null 2>&1
    say "redis 재시작 완료 (DB PENDING 주문 $pending건)"
    for i in $(seq 1 60); do
      sleep 1
      if docker logs $(docker compose ps -q backend | head -1) --since 70s 2>&1 | grep -q 'rebuilt from DB'; then
        say "대기열 재구성 로그 확인 (+${i}s): $(docker logs $(docker compose ps -q backend | head -1) --since 70s 2>&1 | grep -oE 'rebuilt from DB[^"]*' | tail -1)"
        break
      fi
    done
    ;;
  backend)
    docker compose restart backend > /dev/null 2>&1 &
    for i in $(seq 1 240); do
      sleep 1
      # 컨테이너 health 상태는 30초 간격이라 재시작 직후에도 healthy로 남아 있다 → readiness를 직접 확인
      docker exec $(docker compose ps -q backend | head -1) wget -qO- http://127.0.0.1:8091/actuator/health/readiness > /dev/null 2>&1         && [ "$i" -gt 5 ] && { say "readiness UP (+${i}s)"; break; }
    done
    wait
    ;;
  deploy)
    # 배포와 같은 무중단 교체 (같은 이미지로 새 컨테이너 → 이전 것 종료)
    ./rollout.sh 2>&1 | tee -a "$LOG"
    ;;
esac
python3 -c "import time; print('elapsed_s', round(time.time() - $T0, 1))" | tee -a "$LOG"
echo "백엔드 ERROR 로그(최근 4분): $(docker logs $(docker compose ps -q backend | head -1) --since 4m 2>&1 | grep -c '"level":"ERROR"' || true)"
