# Kotlin Screener Dockerfile
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Gradle 의존성 캐싱을 위한 파일 복사
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# 의존성 다운로드 (캐시 활용)
RUN gradle dependencies --no-daemon

# 소스 코드 복사 및 빌드
COPY src ./src
RUN gradle build --no-daemon -x test

# 런타임 이미지
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=build /app/build/libs/*.jar app.jar

# 로그 디렉토리 생성
RUN mkdir -p /app/logs

# 포트 노출
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/health || exit 1

# 애플리케이션 실행
ENTRYPOINT ["java", "-Xmx512m", "-Xms256m", "-jar", "app.jar"]
