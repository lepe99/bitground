#!/bin/bash
# 운영 앱 서버에서 셸 스크립트를 실행하고 결과를 출력한다 (SSM Run Command, root 권한, 작업 폴더 /opt/bitground/deploy/app)
#   사용: AWS_PROFILE=bitground ./ssm-run.sh <로컬 스크립트 파일> [제한 시간(초), 기본 600]
# 스크립트는 base64로 감싸 보내므로 따옴표·$ 이스케이프를 신경 쓰지 않아도 된다. 출력은 앞 24,000자까지만 돌아온다.
set -euo pipefail
FILE=${1:?스크립트 파일}; TIMEOUT=${2:-600}
INSTANCE=${APP_INSTANCE_ID:-i-0b5029efda21bc556}
export AWS_REGION=${AWS_REGION:-ap-northeast-2}
export PYTHONIOENCODING=utf-8
B64=$(base64 -w0 "$FILE")
PARAMS=$(printf '{"commands":["cd /opt/bitground/deploy/app && echo %s | base64 -d > /tmp/lt-cmd.sh && bash /tmp/lt-cmd.sh; rc=$?; rm -f /tmp/lt-cmd.sh; exit $rc"],"executionTimeout":["%s"]}' "$B64" "$TIMEOUT")
ID=$(aws ssm send-command --instance-ids "$INSTANCE" --document-name AWS-RunShellScript \
      --parameters "$PARAMS" --timeout-seconds 60 --query Command.CommandId --output text)
while :; do
  sleep 2
  S=$(aws ssm get-command-invocation --command-id "$ID" --instance-id "$INSTANCE" --query Status --output text 2>/dev/null || echo Pending)
  case $S in Pending|InProgress|Delayed) continue;; esac
  break
done
aws ssm get-command-invocation --command-id "$ID" --instance-id "$INSTANCE" --query StandardOutputContent --output text
ERR=$(aws ssm get-command-invocation --command-id "$ID" --instance-id "$INSTANCE" --query StandardErrorContent --output text)
[ -n "$ERR" ] && [ "$ERR" != "None" ] && echo "[stderr] $ERR" >&2
[ "$S" = Success ] || { echo "[상태] $S" >&2; exit 1; }
