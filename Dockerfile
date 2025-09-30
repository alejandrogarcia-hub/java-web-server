# ========================================
# Stage 1: Build stage with Gradle
# ========================================
FROM eclipse-temurin:21-jdk-jammy AS builder

# Set working directory
WORKDIR /build

# Copy Gradle wrapper and root build files first (for better caching)
COPY gradle/ gradle/
COPY gradlew settings.gradle.kts gradle.properties ./

# Copy checkstyle configuration (required by build)
COPY config/ config/

# Copy app module build file
COPY app/build.gradle.kts app/build.gradle.kts

# Copy application source
COPY app/src/ app/src/

# Build the application (skip tests for faster builds) and reuse Gradle caches between builds
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :app:build -x test --no-daemon

# ========================================
# Stage 2: Production runtime with JRE
# ========================================
FROM eclipse-temurin:21-jre-jammy

# OCI labels for container metadata and GitHub integration
LABEL org.opencontainers.image.source="https://github.com/alejandrogarcia-hub/java-web-server"
LABEL org.opencontainers.image.description="Multi-threaded HTTP/1.1 web server with thread-pooling and keep-alive support, implemented in Java 21"
LABEL org.opencontainers.image.licenses="MIT"
LABEL org.opencontainers.image.title="Java Web Server"
LABEL org.opencontainers.image.vendor="alejandrogarcia-hub"

# Create non-root user for security
RUN groupadd -r webserver && useradd -r -g webserver webserver

# Set working directory
WORKDIR /app

# Copy JAR from builder stage
COPY --from=builder /build/app/build/libs/*.jar app.jar

# Create log directory and set permissions
RUN mkdir -p /var/log/webserver && \
    chown -R webserver:webserver /app /var/log/webserver

# Switch to non-root user
USER webserver

# Expose port
EXPOSE 8080

# Environment variables
ENV ENV=production
ENV LOG_DIR=/var/log/webserver
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD /bin/sh -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && exec 3>&-'

# Run the application
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
