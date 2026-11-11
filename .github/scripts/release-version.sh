#!/usr/bin/env bash
# 릴리즈 버전을 출력한다 (예: 2.0.0). 세 곳의 버전이 하나라도 다르면 실패한다.
#   frontend/package.json "version" · backend/build.gradle version · CHANGELOG.md 맨 위 "## [X.Y.Z]"
# 쓰는 곳: CI(main으로 가는 PR 검사), Deploy(배포 성공 후 태그·릴리즈 생성)
set -euo pipefail
cd "$(dirname "$0")/../.."

frontend=$(sed -n 's/^  "version": "\([^"]*\)",*\r*$/\1/p' frontend/package.json | head -1)
backend=$(sed -n "s/^version = '\([^']*\)'\r*$/\1/p" backend/build.gradle | head -1)
changelog=$(sed -n 's/^## \[\([0-9][0-9.]*[0-9]\)\].*/\1/p' CHANGELOG.md | head -1)

if [ -z "$frontend" ] || [ "$frontend" != "$backend" ] || [ "$frontend" != "$changelog" ]; then
    {
        echo "버전이 맞지 않습니다. 세 곳을 같은 버전으로 맞춰 주세요."
        echo "  frontend/package.json : ${frontend:-(없음)}"
        echo "  backend/build.gradle  : ${backend:-(없음)}"
        echo "  CHANGELOG.md 맨 위    : ${changelog:-(없음)}"
    } >&2
    exit 1
fi
if ! [[ "$frontend" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "버전 형식이 X.Y.Z가 아닙니다: $frontend" >&2
    exit 1
fi
echo "$frontend"
