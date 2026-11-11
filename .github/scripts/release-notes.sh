#!/usr/bin/env bash
# CHANGELOG.md에서 한 버전의 항목(제목 줄 다음부터 다음 "## [" 전까지)을 출력한다
#   사용: release-notes.sh 2.0.0
set -euo pipefail
cd "$(dirname "$0")/../.."
version=${1:?버전이 필요합니다}

notes=$(tr -d '\r' < CHANGELOG.md | awk -v v="$version" '
    index($0, "## [" v "]") == 1 { found = 1; next }
    found && /^## \[/ { exit }
    found { print }
')
if [ -z "$(echo "$notes" | tr -d '[:space:]')" ]; then
    echo "CHANGELOG.md에 $version 항목이 없거나 비어 있습니다" >&2
    exit 1
fi
echo "$notes"
