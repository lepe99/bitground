#!/bin/bash
# 측정 구간의 서버 지표를 모니터링 서버 Prometheus에서 뽑는다 (ssm-run.sh로 실행)
#   TAG=n200 → sample.sh가 남긴 시작·끝 시각 구간에서 (램프업 RAMP_S초 제외) 각 지표의 평균·최대
set -euo pipefail
WORK=/root/loadtest; TAG=${TAG:?TAG}; RAMP_S=${RAMP_S:-60}
START=$(( $(cat "$WORK/$TAG.start") + RAMP_S )); END=$(cat "$WORK/$TAG.end")
PROM=${PROM:-http://10.0.128.10:9090}
python3 - "$PROM" "$START" "$END" <<'EOF'
import json, sys, urllib.parse, urllib.request
prom, start, end = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
B = 'app="bitground-backend"'
API = B + ',uri!~"/actuator.*|/notifications/subscribe|/ws/.*"'
Q = [
    ('server p95 ms (API)', f'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{{{API}}}[1m]))) * 1000'),
    ('server p99 ms (API)', f'histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{{{API}}}[1m]))) * 1000'),
    ('req/s (API)', f'sum(rate(http_server_requests_seconds_count{{{API}}}[1m]))'),
    ('5xx req/s', f'sum(rate(http_server_requests_seconds_count{{{B},status=~"5.."}}[1m])) or vector(0)'),
    ('host CPU %', '100 - avg(rate(node_cpu_seconds_total{mode="idle"}[1m])) * 100'),
    ('host steal %', 'avg(rate(node_cpu_seconds_total{mode="steal"}[1m])) * 100'),
    ('backend process CPU % (of all cores)', f'process_cpu_usage{{{B}}} * 100'),
    ('heap used MiB', f'sum(jvm_memory_used_bytes{{{B},area="heap"}}) / 2^20'),
    ('GC pause ms/s', f'sum(rate(jvm_gc_pause_seconds_sum{{{B}}}[1m])) * 1000'),
    ('hikari active', f'sum(hikaricp_connections_active{{{B}}})'),
    ('hikari pending', f'sum(hikaricp_connections_pending{{{B}}})'),
    ('tomcat busy threads', f'sum(tomcat_threads_busy_threads{{{B}}})'),
    ('live threads', f'sum(jvm_threads_live_threads{{{B}}})'),
    ('host mem available MiB', 'node_memory_MemAvailable_bytes / 2^20'),
    ('swap used MiB', '(node_memory_SwapTotal_bytes - node_memory_SwapFree_bytes) / 2^20'),
    ('redis used MiB', 'redis_memory_used_bytes / 2^20'),
]
print(f'window {start} ~ {end} ({end - start}s)')
for name, expr in Q:
    url = f'{prom}/api/v1/query_range?' + urllib.parse.urlencode({'query': expr, 'start': start, 'end': end, 'step': 15})
    try:
        res = json.load(urllib.request.urlopen(url, timeout=20))['data']['result']
        vals = [float(v) for r in res[:1] for _, v in r['values'] if v not in ('NaN', '+Inf')]
        print(f'{name:38} avg {sum(vals)/len(vals):10.1f}  max {max(vals):10.1f}' if vals else f'{name:38} (no data)')
    except Exception as e:
        print(f'{name:38} error {e}')
EOF
