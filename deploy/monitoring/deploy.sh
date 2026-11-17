#!/bin/bash
# 모니터링 서버 배포 (GitHub Actions deploy 워크플로가 SSM Run Command로 root 권한 실행. 손으로 돌려도 같다)
#   사용: deploy.sh <커밋 해시>
#   1) 저장소는 워크플로가 그 커밋으로 맞춘 상태  2) SSM Parameter Store에서 .env를 다시 만들고
#   3) compose 설정이 바뀐 서비스는 다시 만들고  4) 설정 파일이 바뀐 서비스만 재시작한 뒤
#   5) grafana·loki·prometheus가 응답할 때까지 기다린다 (안 되면 실패로 끝나 워크플로가 빨갛게 된다)
# 이 서버는 인터넷 경로가 없어 외부 통신(SSM·이미지 받기)은 앱 서버 tinyproxy를 거친다 (/etc/profile.d/proxy.sh)
set -euo pipefail

SHA=${1:?커밋 해시가 필요합니다}
REPO=/opt/bitground
export AWS_REGION=${AWS_REGION:-ap-northeast-2}
cd "$REPO/deploy/monitoring"

# SSM 경로 아래 값을 KEY=VALUE 파일로 (값은 화면에 찍지 않는다). deploy/app/deploy.sh와 같은 함수
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

render_env /bitground/prod/monitoring .env

# compose 설정(이미지·환경 변수·마운트)이 바뀐 서비스만 다시 만든다
docker compose up -d --remove-orphans

# 바인드 마운트한 설정 파일은 바뀌어도 compose가 컨테이너를 다시 만들지 않으므로, 바뀐 서비스만 직접 재시작한다.
# Grafana의 경보·데이터 소스 프로비저닝은 시작할 때만 읽는다.
# 처음 배포(.deployed-sha 없음)는 서버에 올라와 있던 설정과 비교할 기준이 없어 전부 재시작한다
config_paths() {
    case "$1" in
        loki)       echo "monitoring/loki/" ;;
        prometheus) echo "monitoring/prometheus/" ;;
        alloy)      echo "deploy/monitoring/alloy/" ;;
        grafana)    echo "monitoring/grafana/ deploy/monitoring/grafana/" ;;
    esac
}
changed=""
if [ -n "$PREV" ]; then
    changed=$(git -C "$REPO" diff --name-only "$PREV" "$SHA" -- monitoring deploy/monitoring || true)
fi
for svc in loki prometheus alloy grafana; do
    restart=false
    if [ -z "$PREV" ]; then
        restart=true
    else
        for p in $(config_paths "$svc"); do
            if grep -q "^$p" <<< "$changed"; then restart=true; fi
        done
    fi
    if [ "$restart" = true ]; then
        if [ -n "$PREV" ]; then echo "$svc 설정 변경 → 재시작"; else echo "$svc 처음 배포 → 재시작"; fi
        docker compose restart "$svc"
    fi
done

# 응답 확인 (Loki는 시작 후 준비까지 십수 초 걸린다). 같은 서버라 프록시를 쓰지 않는다
check() { curl -fsS --noproxy '*' -m 5 -o /dev/null "$1"; }
for target in "grafana http://localhost:3000/api/health" "loki http://localhost:3100/ready" "prometheus http://localhost:9090/-/ready"; do
    name=${target%% *} url=${target#* }
    ok=false
    for _ in $(seq 1 24); do
        if check "$url"; then ok=true; break; fi
        sleep 5
    done
    if [ "$ok" != true ]; then
        echo "$name 이(가) 응답하지 않습니다 ($url)" >&2
        docker compose logs --tail 80 "$name" >&2
        exit 1
    fi
    echo "$name 정상"
done

echo "$SHA" > .deployed-sha
echo "모니터링 서버 배포 완료: $SHA"
