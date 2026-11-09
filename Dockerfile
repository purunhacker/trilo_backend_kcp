FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Add non-root user
RUN addgroup -S trilo && adduser -S trilo -G trilo
USER trilo

# Copy the JAR file (context is the service directory)
COPY build/libs/*.jar app.jar

# Expose default Spring Boot port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
