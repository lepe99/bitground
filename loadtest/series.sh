#!/bin/bash
# S1 단계 부하를 차례로 실행: ./series.sh 50 100 200 ... (단계 사이 60초 휴식)
cd "$(dirname "$0")"
for n in "$@"; do
  echo "##### N=$n $(date +%T)"
  ./run.sh "$n" "${HOLD:-5m}" "${PREFIX:-s1-}n$n" 2>&1 | tail -30
  sleep 60
done
echo "##### done $(date +%T)"
