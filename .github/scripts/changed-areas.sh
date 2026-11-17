#!/usr/bin/env bash
# 기준 커밋 이후 바뀐 영역을 판정해 KEY=true|false 줄로 출력한다 (판정 근거는 stderr로).
#   사용: changed-areas.sh [기준 커밋] [대상 커밋=HEAD]   (기준이 비었거나 없는 커밋이면 전체)
#   출력: backend · server · scheduler · frontend · monitoring
# 쓰는 곳: CI의 '변경 영역' job. 결과에 따라 테스트·이미지 빌드·Deploy 단계를 건너뛴다.
#
# 원칙: 애매하면 더 많이 실행하는 쪽으로 판정한다 (느려질 뿐 배포가 빠지지는 않게)
#   - 영역에 속하지 않고 무관 목록에도 없는 경로가 바뀌면 전체 (.github/ 포함, 새로 생긴 폴더도)
#   - 버전 줄만 바뀐 build.gradle · package.json · package-lock.json은 바뀌지 않은 것으로 본다.
#     릴리즈마다 버전을 올리므로 이걸 세면 매번 전체가 돈다. 버전 값은 실행 중에 쓰이지 않는다
#     (buildInfo 없음, 프론트도 package.json 버전을 읽지 않음). 의존성이 바뀌면 다른 줄도 바뀌어 잡힌다.
set -euo pipefail
cd "$(dirname "$0")/../.."

base=${1:-}
head=${2:-HEAD}
backend=false server=false scheduler=false frontend=false monitoring=false

print() { printf 'backend=%s\nserver=%s\nscheduler=%s\nfrontend=%s\nmonitoring=%s\n' "$backend" "$server" "$scheduler" "$frontend" "$monitoring"; }
all() { echo "→ 전체: $1" >&2; backend=true server=true scheduler=true frontend=true monitoring=true; print; exit 0; }

[ -n "$base" ] || all "기준 커밋 없음"
git cat-file -e "$base^{commit}" 2>/dev/null || all "기준 커밋 $base 을(를) 찾을 수 없음"
echo "기준: $(git rev-parse --short "$base^{commit}") ($base) → $(git rev-parse --short "$head")" >&2

# 그 파일에서 버전 줄 말고 바뀐 줄이 없으면 참
version_only() {
    local pattern
    case "$1" in
        backend/build.gradle) pattern="^version = '" ;;
        *) pattern='^ *"version": ' ;;
    esac
    # grep -q로 일찍 끝내면 앞 단계가 SIGPIPE로 죽고 pipefail이 결과를 뒤집으므로, 전부 받아서 비었는지 본다
    local other
    other=$(git diff -U0 "$base" "$head" -- "$1" | grep -E '^[+-]' | grep -vE '^(\+\+\+|---) ' \
        | cut -c2- | grep -vE "$pattern" || true)
    [ -z "$other" ]
}

while IFS= read -r f; do
    case "$f" in
        backend/build.gradle|frontend/package.json|frontend/package-lock.json)
            if version_only "$f"; then echo "  $f: 버전 줄만 바뀜 → 무시" >&2; continue; fi ;;
    esac
    case "$f" in
        # 운영 배포와 무관: 문서, 부하 테스트, 로컬 전용 compose
        *.md|docs/*|loadtest/*|docker-compose.yml) ;;
        # 모니터링 서버: 운영 compose·경보(deploy/monitoring)와 로컬과 같이 쓰는 설정(monitoring).
        # monitoring/alloy는 로컬 전용이지만 나눠 판정하지 않는다 (서버에서는 바뀐 서비스가 없어 재시작 없이 끝난다)
        monitoring/*|deploy/monitoring/*) monitoring=true ;;
        backend/*)    backend=true ;;
        scheduler/*)  scheduler=true ;;
        frontend/*)   frontend=true ;;
        deploy/app/*) server=true ;;
        *) all "$f 은(는) 어느 영역에도 속하지 않음" ;;
    esac
done < <(git diff --name-only "$base" "$head")

print
