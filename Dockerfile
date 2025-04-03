FROM openjdk:11-jre-slim

WORKDIR /app
COPY target/scala-2.13/sirjin-data-service.jar /app/
COPY .env /app/

# 환경 변수 설정을 위한 스크립트 추가
COPY docker-entrypoint.sh /app/
RUN chmod +x /app/docker-entrypoint.sh

EXPOSE 8080
EXPOSE 2551

ENTRYPOINT ["/app/docker-entrypoint.sh"] 