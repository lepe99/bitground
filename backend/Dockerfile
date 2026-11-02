# 1) Build 단계: Gradle Wrapper 기반
FROM eclipse-temurin:17-jdk-alpine AS builder
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

# 2) Run 단계: JRE만 포함해 경량화
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Pinpoint Agent 파일 복사
COPY pinpoint-agent /app/pinpoint-agent

# builder 단계에서 만들어진 JAR 복사
ARG JAR_FILE=build/libs/*.jar
COPY --from=builder /app/${JAR_FILE} app.jar

EXPOSE 8090
ENTRYPOINT ["java", \
            "-javaagent:/app/pinpoint-agent/pinpoint-bootstrap.jar", \
            "-Dpinpoint.agentId=bitground", \
            "-Dpinpoint.applicationName=bitground-spring", \
            "-Duser.timezone=Asia/Seoul", \
            "-jar", \
            "app.jar"]

