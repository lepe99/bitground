#!/bin/bash
# S1 한 단계 실행: 서버 기록 시작 → k6 → 서버 기록 중지·지표 수집. 결과는 results/raw/<TAG>/
#   사용: AWS_PROFILE=bitground ./run.sh <동시 사용자 N> [유지 시간, 기본 5m] [TAG, 기본 nN]
set -euo pipefail
cd "$(dirname "$0")"
N=${1:?N}; HOLD=${2:-5m}; TAG=${3:-n$N}; RAMP=${RAMP:-1m}
OUT=results/raw/$TAG; mkdir -p "$OUT"
POSTS=$(cat results/posts.txt 2>/dev/null || echo "")
tmp=$(mktemp)

server() { printf 'ACTION=%s TAG=%s RAMP_S=%s\n' "$1" "$TAG" 60 > "$tmp"; cat "server/$2" >> "$tmp"; ./ssm-run.sh "$tmp" 300; }

server start sample.sh | tee "$OUT/server-start.txt"
# 내 PC(부하 발생기) 자원도 같이 기록: k6 컨테이너 CPU가 높으면 결과를 믿을 수 없다
( while sleep 10; do docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' 2>/dev/null | grep "k6-$TAG" | sed "s/^/$(date +%T) /"; done ) > "$OUT/generator.stats" &
GEN=$!

MSYS_NO_PATHCONV=1 docker run --rm --name "k6-$TAG" -v "$(cygpath -w "$PWD" 2>/dev/null || pwd):/scripts" grafana/k6:2.3.0 run \
  -e N="$N" -e HOLD="$HOLD" -e RAMP="$RAMP" -e POSTS="$POSTS" \
  --summary-export "/scripts/$OUT/summary.json" /scripts/capacity.js > "$OUT/k6.log" 2>&1 || true
kill $GEN 2>/dev/null || true

server stop sample.sh | tee "$OUT/server-stop.txt"
server x prom.sh | tee "$OUT/prometheus.txt"
rm -f "$tmp"
echo "== k6 요약"
grep -E "p\(95\)<500|rate<0.01|api_errors\.|page_time\.|http_req_duration\{kind:api\}|http_reqs\.|vus_max|ws_messages|orders_placed" -A0 "$OUT/k6.log" | head -20
grep -A1 'kind:api' "$OUT/k6.log" | head -2
echo "== 발생기 CPU 최대: $(awk '{gsub("%","",$3); if($3>m)m=$3} END{print m"%"}' "$OUT/generator.stats")"
