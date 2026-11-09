#!/bin/sh
# 운영 Lambda용 바이너리 (provided.al2023 런타임, arm64). dist/scheduler-lambda.zip
# 수집 단계(A)와 쓰기 단계(B) 두 함수가 같은 zip을 쓰고 환경변수 SCHEDULER_ROLE로 역할을 나눈다
#   A: SCHEDULER_ROLE=collector, WRITER_FUNCTION=<B 이름>, 외부 API 키 (VPC 밖)
#   B: SCHEDULER_ROLE=writer, DB_*, BACKEND_URL(사설 IP), SEASON_UPDATE_KEY (VPC 안)
set -e
cd "$(dirname "$0")"
mkdir -p dist
GOOS=linux GOARCH=arm64 CGO_ENABLED=0 go build -tags lambda.norpc -trimpath -ldflags="-s -w" -o dist/bootstrap .
if command -v zip >/dev/null 2>&1; then
  (cd dist && rm -f scheduler-lambda.zip && zip -q scheduler-lambda.zip bootstrap)
  echo "dist/scheduler-lambda.zip"
else
  echo "dist/bootstrap (zip이 없어 압축은 건너뜀)"
fi
