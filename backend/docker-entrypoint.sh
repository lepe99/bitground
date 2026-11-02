#!/bin/sh
# Spring 앱 실행 스크립트.
# PINPOINT_ENABLED=true 이면 Pinpoint 에이전트를 붙여 트랜잭션을 추적한다. (기본: 끔)
#
#   PINPOINT_COLLECTOR_HOST    수집 서버 주소 (켤 때 필수)
#   PINPOINT_PROFILE           local(전체 수집) | release(5건 중 1건)   기본 local
#   PINPOINT_SERVICE_NAME      Pinpoint 4.x 서비스 이름                 기본 DEFAULT
#   PINPOINT_APPLICATION_NAME  애플리케이션 이름                        기본 bitground-spring
#   PINPOINT_AGENT_NAME        에이전트 표시 이름                       기본 bitground
set -e

JAVA_OPTS="-Duser.timezone=Asia/Seoul ${JAVA_OPTS}"

if [ "${PINPOINT_ENABLED:-false}" = "true" ]; then
  : "${PINPOINT_COLLECTOR_HOST:?PINPOINT_ENABLED=true 이면 PINPOINT_COLLECTOR_HOST 가 필요합니다}"
  JAVA_OPTS="${JAVA_OPTS} -javaagent:/opt/pinpoint-agent/pinpoint-bootstrap.jar"
  JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.profiler.profiles.active=${PINPOINT_PROFILE:-local}"
  JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.serviceName=${PINPOINT_SERVICE_NAME:-DEFAULT}"
  JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.applicationName=${PINPOINT_APPLICATION_NAME:-bitground-spring}"
  JAVA_OPTS="${JAVA_OPTS} -Dpinpoint.agentName=${PINPOINT_AGENT_NAME:-bitground}"
  JAVA_OPTS="${JAVA_OPTS} -Dprofiler.transport.grpc.collector.ip=${PINPOINT_COLLECTOR_HOST}"
  echo "[entrypoint] Pinpoint agent enabled -> ${PINPOINT_COLLECTOR_HOST} (profile: ${PINPOINT_PROFILE:-local})"
fi

# shellcheck disable=SC2086
exec java ${JAVA_OPTS} -jar /app/app.jar
