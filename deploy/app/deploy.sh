#!/bin/bash
# 앱 서버 배포 (GitHub Actions deploy 워크플로가 SSM Run Command로 root 권한 실행. 손으로 돌려도 같다)
#   사용: deploy.sh <커밋 해시>
#   1) 저장소를 그 커밋으로 맞추고  2) SSM Parameter Store에서 .env·backend.env를 다시 만들고
#   3) 백엔드 이미지를 그 커밋 태그로 받아 올린 뒤  4) 바뀐 설정(nginx·alloy·tinyproxy)이 있으면 그 서비스만 다시 띄우고
#   5) 백엔드가 healthy가 될 때까지 기다린다 (안 되면 실패로 끝나 워크플로가 빨갛게 된다)
# 이 스크립트는 받아 온 새 커밋의 것이 실행되도록, 워크플로가 먼저 checkout한 다음 부른다.
set -euo pipefail

SHA=${1:?커밋 해시가 필요합니다}
REPO=/opt/bitground
export AWS_REGION=${AWS_REGION:-ap-northeast-2}
cd "$REPO/deploy/app"

# SSM 경로 아래 값을 KEY=VALUE 파일로 (값은 화면에 찍지 않는다)
render_env() {
    ( umask 077
      aws ssm get-parameters-by-path --path "$1" --with-decryption \
          --query 'Parameters[].[Name,Value]' --output text \
      | while IFS=$'\t' read -r name value; do printf '%s=%s\n' "${name##*/}" "$value"; done > "$2.new" )
    [ -s "$2.new" ] || { echo "$1 에서 값을 읽지 못했습니다" >&2; exit 1; }
    mv "$2.new" "$2"
    echo "$2: $(wc -l < "$2")개 키"
}

PREV=$(cat .deployed-sha 2>/dev/null || echo "")
echo "배포: ${PREV:-(처음)} → $SHA"

render_env /bitground/prod/app .env
echo "BACKEND_TAG=$SHA" >> .env
render_env /bitground/prod/backend backend.env

docker compose pull -q backend
docker compose up -d --remove-orphans

# 바인드 마운트한 설정 파일은 바뀌어도 compose가 컨테이너를 다시 만들지 않으므로, 바뀐 서비스만 직접 재시작한다
if [ -n "$PREV" ]; then
    changed=$(git -C "$REPO" diff --name-only "$PREV" "$SHA" -- deploy/app || true)
    for svc in nginx alloy tinyproxy; do
        if echo "$changed" | grep -q "^deploy/app/$svc/"; then
            echo "$svc 설정 변경 → 재시작"
            docker compose restart "$svc"
        fi
    done
fi

for _ in $(seq 1 36); do
    status=$(docker inspect -f '{{.State.Health.Status}}' bitground-backend-1 2>/dev/null || echo missing)
    [ "$status" = healthy ] && break
    sleep 5
done
if [ "$status" != healthy ]; then
    echo "백엔드가 healthy가 되지 않았습니다 ($status)" >&2
    docker compose logs --tail 80 backend >&2
    exit 1
fi

echo "$SHA" > .deployed-sha
echo "backend: $(docker inspect -f '{{index .Config.Labels "org.opencontainers.image.revision"}}' bitground-backend-1) healthy"
docker compose ps --format '{{.Name}} {{.Status}}'
