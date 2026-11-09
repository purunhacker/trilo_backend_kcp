# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy gradle files first for better caching
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
COPY common-lib/build.gradle.kts common-lib/
COPY media-service/build.gradle.kts media-service/

# Download dependencies
RUN ./gradlew :media-service:dependencies --no-daemon || true

# Copy source
COPY common-lib/src common-lib/src
COPY media-service/src media-service/src

# Build
RUN ./gradlew :media-service:bootJar --no-daemon -x test

# Stage 2: Runtime - Use full JRE for image processing libraries
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Install image processing dependencies
RUN apk add --no-cache ffmpeg imagemagick

# Security: non-root user
RUN addgroup -S trilo && adduser -S trilo -G trilo

# Copy JAR
COPY --from=builder /build/media-service/build/libs/*.jar app.jar

# Set ownership
RUN chown -R trilo:trilo /app
USER trilo

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

# Pin the server clock to UTC so LocalDate.now()/cron expressions never depend on the host
# timezone. Users live in many timezones; wall-clock defaults belong to the client, not the server.
ENV TZ=UTC

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-Duser.timezone=UTC", "-jar", "app.jar"]
