#!/bin/sh

# .env 파일에서 환경 변수 로드
set -a
. /app/.env
set +a

# 애플리케이션 실행
exec java -jar /app/sirjin-data-service.jar 