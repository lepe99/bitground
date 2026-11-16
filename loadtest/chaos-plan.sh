#!/bin/bash
# 부하가 도는 동안 정해진 시각에 장애를 넣는다: ./chaos-plan.sh HH:MM:SS=redis HH:MM:SS=backend ...
cd "$(dirname "$0")"
S=$(mktemp)
for ev in "$@"; do
  at=${ev%%=*}; target=${ev##*=}
  wait_s=$(( $(date -d "$at" +%s) - $(date +%s) )); [ $wait_s -gt 0 ] && sleep $wait_s
  echo "##### $(date +%T) $target"
  printf 'TARGET=%s\n' "$target" | cat - server/chaos.sh > "$S"
  ./ssm-run.sh "$S" 400
done
rm -f "$S"
