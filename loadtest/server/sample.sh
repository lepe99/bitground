#!/bin/bash
# 측정 구간 표시 + 컨테이너 자원 기록 (ssm-run.sh로 실행)
#   ACTION=start TAG=n200  → 데드락 수 기록, 10초마다 docker stats를 /root/loadtest/<TAG>.stats 에 쌓기 시작
#   ACTION=stop  TAG=n200  → 기록 중지, 컨테이너별 CPU·메모리 최대/평균, 구간 동안 늘어난 데드락 수
set -euo pipefail
WORK=/root/loadtest; mkdir -p "$WORK"
TAG=${TAG:?TAG}
q() { docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot -N bitground'; }
deadlocks() { echo "SELECT COUNT FROM information_schema.INNODB_METRICS WHERE NAME='lock_deadlocks';" | q; }

if [ "${ACTION:?start|stop}" = start ]; then
  deadlocks > "$WORK/$TAG.deadlocks"
  date +%s > "$WORK/$TAG.start"
  setsid nohup bash -c "while true; do docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' | sed \"s/^/\$(date +%H:%M:%S) /\"; sleep 10; done" \
    > "$WORK/$TAG.stats" 2>&1 < /dev/null &
  echo $! > "$WORK/$TAG.pid"
  echo "start $TAG $(cat "$WORK/$TAG.start") deadlocks=$(cat "$WORK/$TAG.deadlocks")"
else
  pkill -g "$(cat "$WORK/$TAG.pid")" 2>/dev/null || kill "$(cat "$WORK/$TAG.pid")" 2>/dev/null || true
  date +%s > "$WORK/$TAG.end"
  echo "window $(cat "$WORK/$TAG.start") $(cat "$WORK/$TAG.end")"
  echo "deadlocks +$(( $(deadlocks) - $(cat "$WORK/$TAG.deadlocks") ))"
  python3 - "$WORK/$TAG.stats" <<'EOF'
import sys, re, collections
cpu, mem = collections.defaultdict(list), collections.defaultdict(list)
unit = {'B': 1 / 2**20, 'KiB': 1 / 1024, 'MiB': 1, 'GiB': 1024}
for line in open(sys.argv[1]):
    p = line.split()
    if len(p) < 4 or not p[1].startswith('bitground-'): continue
    name = p[1][10:-2]
    cpu[name].append(float(p[2].rstrip('%')))
    m = re.match(r'([\d.]+)(\w+)', p[3]); mem[name].append(float(m.group(1)) * unit[m.group(2)])
print(f"{'container':10} {'cpu avg':>8} {'cpu max':>8} {'mem avg':>8} {'mem max':>8}  (cpu%: 100 = one core)")
for k in sorted(cpu):
    print(f"{k:10} {sum(cpu[k])/len(cpu[k]):8.1f} {max(cpu[k]):8.1f} {sum(mem[k])/len(mem[k]):8.0f} {max(mem[k]):8.0f}")
EOF
  echo "backend log lines with ERROR or Deadlock:"
  since=$(( $(date +%s) - $(cat "$WORK/$TAG.start") + 5 ))
  docker logs bitground-backend-1 --since "${since}s" 2>&1 | grep -c -E '"level":"ERROR"|Deadlock' || true
  echo "nginx error log (worker_connections / alert / emerg):"
  docker logs bitground-nginx-1 --since "${since}s" 2>&1 | grep -E 'worker_connections|\[alert\]|\[emerg\]|\[crit\]' | sed -E 's/^.*[0-9]+#[0-9]+: //' | cut -c1-160 | sort | uniq -c | sort -rn | head -5
  echo "oom-kill (kernel, this window): $(journalctl -k --since "@$(cat "$WORK/$TAG.start")" --no-pager 2>/dev/null | grep -c 'Killed process' || true)"
fi
