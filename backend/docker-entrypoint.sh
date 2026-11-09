#!/bin/sh
# Spring 앱 실행 스크립트. JAVA_OPTS 로 JVM 옵션을 더할 수 있다.
set -e

JAVA_OPTS="-Duser.timezone=Asia/Seoul ${JAVA_OPTS}"

# shellcheck disable=SC2086
exec java ${JAVA_OPTS} -jar /app/app.jar
