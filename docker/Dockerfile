FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Gradle wrapper와 소스 복사
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
COPY src src

# 실행 권한 부여 및 빌드
RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

# 실행 이미지
FROM eclipse-temurin:21-jre

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 프로파일 인자 (기본값: dev)
ARG PROFILE=dev
ENV SPRING_PROFILES_ACTIVE=${PROFILE}

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
