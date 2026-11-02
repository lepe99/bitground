# 1) Build 단계: Gradle Wrapper 기반
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# gradlew, gradle 설정 복사 (캐시 활용)
COPY gradlew .
COPY gradle gradle/

# 실제 빌드 스크립트 복사
# Groovy DSL : build.gradle/settings.gradle,
COPY build.gradle settings.gradle ./

# 소스 코드 복사
COPY src src

# 권한 부여 후 빌드
RUN chmod +x gradlew \
 && ./gradlew clean bootJar -x test --no-daemon

# 2) Pinpoint 에이전트 단계: 공식 릴리스를 내려받아 팀 설정을 적용
#    (32MB 바이너리를 레포에 넣지 않기 위해 빌드 시 다운로드, 체크섬으로 무결성 확인)
FROM alpine:3.20 AS pinpoint
ARG PINPOINT_VERSION=4.0.0
ARG PINPOINT_SHA256=739ed672025aa6d97a8e8aa8ccc8387be9a15fa7ad3ecf80a0c51d5ad6938385
RUN wget -qO /tmp/agent.tgz "https://github.com/pinpoint-apm/pinpoint/releases/download/v${PINPOINT_VERSION}/pinpoint-agent-${PINPOINT_VERSION}.tar.gz" \
 && echo "${PINPOINT_SHA256}  /tmp/agent.tgz" | sha256sum -c - \
 && mkdir -p /opt \
 && tar -xzf /tmp/agent.tgz -C /opt \
 && mv /opt/pinpoint-agent-${PINPOINT_VERSION} /opt/pinpoint-agent \
 # 운영(release) 프로필 샘플링: 5건 중 1건 (기존 3.0.1 설정 유지, 4.0 기본값은 20)
 && sed -i 's/^profiler.sampling.counting.sampling-rate=.*/profiler.sampling.counting.sampling-rate=5/' \
        /opt/pinpoint-agent/profiles/release/pinpoint.config

# 3) Run 단계: JRE만 포함해 경량화
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root가 아닌 전용 사용자로 실행
RUN addgroup -S app && adduser -S -G app app

# Pinpoint 에이전트는 실행 중 자기 디렉터리에 로그를 쓰므로 소유자를 앱 사용자로
COPY --from=pinpoint --chown=app:app /opt/pinpoint-agent /opt/pinpoint-agent

# builder 단계에서 만들어진 JAR 복사
ARG JAR_FILE=build/libs/*.jar
COPY --from=builder /app/${JAR_FILE} app.jar

# PINPOINT_ENABLED=true 일 때만 에이전트를 붙여 실행
COPY --chmod=755 docker-entrypoint.sh /app/docker-entrypoint.sh

USER app

# 컨테이너 메모리 한도 기준으로 힙을 잡는다 (1GB 인스턴스에서 메타스페이스·OS·Redis 몫을 남기도록 60%)
# OOM이 나면 비정상 상태로 버티지 않고 종료해 재시작되게 한다
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8090 8091
# 관리 포트의 liveness 프로브로 상태 확인 (busybox wget 사용)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3   CMD wget -qO- http://127.0.0.1:8091/actuator/health/liveness > /dev/null || exit 1

ENTRYPOINT ["/app/docker-entrypoint.sh"]
