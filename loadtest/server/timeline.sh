#!/bin/bash
# Nginx 접속 로그(JSON 한 줄)로 시간대별 응답 상태를 집계한다 (ssm-run.sh로 실행)
#   FROM=HH:MM:SS TO=HH:MM:SS [STEP=10]  → STEP초마다 요청 수, 2xx·4xx·5xx·499(클라이언트가 먼저 끊음), 평균·최대 처리 시간
set -euo pipefail
FROM=${FROM:?}; TO=${TO:?}; STEP=${STEP:-10}
docker logs bitground-nginx-1 --since "$(date -d "$FROM" +%s)" --until "$(date -d "$TO" +%s)" 2>/dev/null \
 | python3 -c "
import sys, json, collections, datetime
step = int(sys.argv[1]); b = collections.OrderedDict()
for line in sys.stdin:
    if not line.startswith('{'): continue
    try: r = json.loads(line)
    except Exception: continue
    if r.get('host', '').startswith('grafana'): continue
    t = datetime.datetime.fromisoformat(r['ts']).astimezone(datetime.timezone(datetime.timedelta(hours=9)))
    k = t.replace(second=t.second - t.second % step, microsecond=0).strftime('%H:%M:%S')
    s = r['status']; d = b.setdefault(k, collections.Counter()); d['n'] += 1
    d['2xx' if s < 300 else '4xx' if s < 499 else '499' if s == 499 else '5xx' if s < 600 else 'x'] += 1
    if 'sse' in r['uri'] or 'subscribe' in r['uri']: d['sse'] += 1
    d['dsum'] += r['duration']; d['dmax'] = max(d['dmax'], r['duration'] if '/subscribe' not in r['uri'] and '/ws/' not in r['uri'] else 0)
print(f\"{'time':8} {'req':>5} {'2xx':>5} {'4xx':>4} {'5xx':>4} {'499':>4} {'sse':>4} {'max_s':>6}\")
for k, d in b.items():
    print(f\"{k:8} {d['n']:5} {d['2xx']:5} {d['4xx']:4} {d['5xx']:4} {d['499']:4} {d['sse']:4} {d['dmax']:6.2f}\")
" "$STEP"
