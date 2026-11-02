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

COPY --from=pinpoint /opt/pinpoint-agent /opt/pinpoint-agent

# builder 단계에서 만들어진 JAR 복사
ARG JAR_FILE=build/libs/*.jar
COPY --from=builder /app/${JAR_FILE} app.jar

# PINPOINT_ENABLED=true 일 때만 에이전트를 붙여 실행
COPY docker-entrypoint.sh /app/docker-entrypoint.sh
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8090
ENTRYPOINT ["/app/docker-entrypoint.sh"]
