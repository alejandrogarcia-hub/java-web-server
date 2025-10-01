# Docker Deployment Guide

This document describes how to build and deploy the Java web server using Docker.

## 🐳 Quick Start

### Using Make (Recommended)

```bash
# Build the Docker image
make docker-build

# Run with docker-compose (recommended)
make docker-up

# View logs
make docker-logs

# Stop the container
make docker-down

# Clean up everything (containers + image)
make docker-clean
```

### Manual Docker Commands

```bash
# Build the image
docker build -t java-web-server:latest .

# Run the container
docker run -p 8080:8080 -e ENV=production --name java-web-server java-web-server:latest

# Run with docker-compose
docker-compose up -d
```

## 📦 Docker Image Details

### Multi-Stage Build

The Dockerfile uses a **two-stage build** for optimal size and security:

**Stage 1: Builder** (eclipse-temurin:21-jdk-jammy)

- Compiles the application with Gradle
- Downloads all dependencies
- Builds the JAR file

**Stage 2: Runtime** (eclipse-temurin:21-jre-jammy)

- Minimal JRE-only base image
- Copies only the compiled JAR
- Includes curl for health checks
- Runs as non-root user

### Image Size

- **Final Image:** ~288MB
- **Base JRE:** ~120MB
- **Application + Dependencies:** ~168MB

## 🔒 Security Features

1. **Non-root User**
   - Runs as `webserver` user (not root)
   - Improves container security

2. **Minimal Base Image**
   - Uses JRE-only (no JDK) for runtime
   - Reduces attack surface

3. **No Build Tools in Production**
   - Build tools (Gradle, JDK) only in builder stage
   - Not included in final image

## ⚙️ Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `ENV` | `production` | Environment mode (`dev` or `production`) |
| `LOG_DIR` | `/var/log/webserver` | Log directory path |
| `JAVA_OPTS` | `-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport` | JVM options |

### Port Mapping

- **Container Port:** 8080 (driven by `SERVER_PORT`)
- **Host Port:** 8080 by default; customize with `HOST_PORT` in `docker-compose.yml`.

> **Note:** Update `SERVER_PORT` and `HOST_PORT` together so the container's health check and the published port stay in sync.

### Volume Mounts

Logs are persisted to the host:

```yaml
volumes:
  - ./logs:/var/log/webserver
```

## 🏥 Health Check

The container includes a health check that:

- Runs every 30 seconds
- Times out after 10 seconds
- Allows 40 seconds for startup
- Retries 3 times before marking unhealthy

```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1
```

## 📊 JVM Container Optimization

The container uses container-aware JVM settings:

- **`-XX:MaxRAMPercentage=75.0`**: Uses up to 75% of container memory
- **`-XX:+UseContainerSupport`**: Enables container-aware resource detection

### Memory Limits

Set memory limits in docker-compose.yml:

```yaml
services:
  web-server:
    deploy:
      resources:
        limits:
          memory: 512M
        reservations:
          memory: 256M
```

## 🚀 Production Deployment

### Docker Compose (Recommended)

```yaml
version: '3.9'

services:
  web-server:
    image: java-web-server:latest
    container_name: java-web-server
    ports:
      - "8080:8080"
    environment:
      - ENV=production
      - LOG_DIR=/var/log/webserver
    volumes:
      - ./logs:/var/log/webserver
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: java-web-server
spec:
  replicas: 3
  selector:
    matchLabels:
      app: java-web-server
  template:
    metadata:
      labels:
        app: java-web-server
    spec:
      containers:
      - name: web-server
        image: java-web-server:latest
        ports:
        - containerPort: 8080
        env:
        - name: ENV
          value: "production"
        - name: LOG_DIR
          value: "/var/log/webserver"
        resources:
          limits:
            memory: "512Mi"
            cpu: "500m"
          requests:
            memory: "256Mi"
            cpu: "250m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 40
          periodSeconds: 30
        readinessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 10
```

## 🔍 Troubleshooting

### View Container Logs

```bash
# Using docker-compose
docker-compose logs -f web-server

# Using docker
docker logs -f java-web-server
```

### Access Container Shell

```bash
# Get container ID
docker ps

# Execute shell
docker exec -it java-web-server bash
```

### Check Health Status

```bash
# Inspect health check
docker inspect --format='{{.State.Health.Status}}' java-web-server
```

### Common Issues

**Container exits immediately:**

- Check logs: `docker logs java-web-server`
- Verify JAR file exists in image: `docker run --rm java-web-server ls -la /app/`

**Health check failing:**

- Verify application is listening on port 8080
- Check if `/health` endpoint exists
- Increase `start_period` in health check

**Out of memory:**

- Increase container memory limits
- Adjust `MaxRAMPercentage` in `JAVA_OPTS`

## 📦 Registry Deployment

### Push to Docker Hub

```bash
# Tag the image
docker tag java-web-server:latest your-username/java-web-server:latest

# Login to Docker Hub
docker login

# Push the image
docker push your-username/java-web-server:latest
```

### Private Registry

```bash
# Tag for private registry
docker tag java-web-server:latest registry.example.com/java-web-server:latest

# Push to private registry
docker push registry.example.com/java-web-server:latest
```

## 🏗️ Build Optimization

### Layer Caching

The Dockerfile is optimized for Docker layer caching:

1. Copy Gradle wrapper and build files first
2. Download dependencies (cached if build files unchanged)
3. Copy source code (most frequently changed)
4. Build application

This ensures fast rebuilds when only source code changes.

### Build Performance

```bash
# Build without cache (clean build)
docker build --no-cache -t java-web-server:latest .

# Build with specific target stage
docker build --target builder -t java-web-server:builder .
```
