#!/bin/bash
# 백엔드 무중단 교체 (deploy.sh가 부른다. 손으로 돌려도 된다: cd /opt/bitground/deploy/app && ./rollout.sh)
#   1) 지금 컨테이너는 그대로 두고 새 설정·이미지로 컨테이너를 하나 더 띄운다 (compose --scale 2 --no-recreate)
#   2) 새 컨테이너가 healthy가 되면, nginx가 Docker DNS를 다시 확인(5초)해 두 컨테이너로 나눠 보낼 때까지 기다린다
#   3) 이전 컨테이너를 정상 종료(SIGTERM → 새 요청 거절, 처리 중인 요청 마무리). nginx는 연결이 안 되는 쪽을 건너뛴다
#   4) 다시 1개로 맞춘다
# 새 컨테이너가 healthy가 되지 않으면 새 것만 지우고 이전 컨테이너를 그대로 둔다 (자동 롤백)
set -euo pipefail
cd "$(dirname "$0")"
WAIT_HEALTHY=${WAIT_HEALTHY:-240}
log() { echo "[rollout $(date +%T)] $*"; }

old=$(docker compose ps -q backend || true)
if [ -z "$old" ]; then
    log "실행 중인 백엔드 없음 → 새로 시작"
    docker compose up -d --no-deps backend
    old=""
else
    log "이전: $(echo $old | cut -c1-12)"
    docker compose up -d --no-deps --no-recreate --scale backend=$(( $(echo "$old" | wc -l) + 1 )) backend
fi

new=$(docker compose ps -q backend | grep -v -F -x -f <(echo "${old:-none}") || true)
[ -n "$new" ] || { log "새 컨테이너를 찾지 못함"; exit 1; }
log "새: $(echo $new | cut -c1-12) ($(docker inspect -f '{{.Name}}' $new))"

status=starting
for _ in $(seq 1 $((WAIT_HEALTHY / 3))); do
    status=$(docker inspect -f '{{.State.Health.Status}}' "$new" 2>/dev/null || echo missing)
    [ "$status" = healthy ] && break
    [ "$status" = unhealthy ] && break
    sleep 3
done
if [ "$status" != healthy ]; then
    log "새 컨테이너가 healthy가 되지 않음 ($status) → 새 것만 지우고 이전 컨테이너 유지"
    docker logs --tail 80 "$new" >&2 || true
    docker rm -f "$new" > /dev/null
    [ -n "$old" ] && docker compose up -d --no-deps --no-recreate --scale backend=$(echo "$old" | wc -l) backend
    exit 1
fi
log "새 컨테이너 healthy"

if [ -n "$old" ]; then
    sleep 6   # nginx resolver valid=5s: 새 컨테이너 주소를 알게 될 때까지
    for c in $old; do
        log "이전 컨테이너 정상 종료: $(docker inspect -f '{{.Name}}' $c)"
        docker stop -t 30 "$c" > /dev/null
        docker rm "$c" > /dev/null
    done
    docker compose up -d --no-deps --no-recreate --scale backend=1 backend
fi
log "완료: $(docker compose ps --format '{{.Name}} {{.Status}}' backend)"
