# Java Web Server

A multi-threaded, file-based HTTP/1.1 web server with thread-pooling and keep-alive support, implemented in Java 21.

## 🚀 Quick Start

### Prerequisites

- **Java 21** (JDK)
- **Gradle 9.1** (build system)
- **Docker** (optional, for containerized deployment)

### Build

```bash
# Build the project
./gradlew build

# Or use make
make pipeline
```

### Run

```bash
# Run in development mode
make run-dev

# Run in production mode
make run-prod

# Or use gradle directly
./gradlew run
```

### Test

```bash
# Run all tests
./gradlew test

# Or use make
make test
```

## ⚙️ Configuration

The server can be configured using environment variables:

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP listening port | `8080` |
| `SERVER_ACCEPT_TIMEOUT_MS` | Accept loop timeout (milliseconds) | `5000` |
| `SERVER_BACKLOG` | Connection queue size | `100` |
| `SERVER_SHUTDOWN_TIMEOUT_SEC` | Graceful shutdown timeout (seconds) | `30` |
| `SERVER_CLIENT_SO_TIMEOUT_MS` | Client socket read timeout (milliseconds) | `15000` |
| `ENV` | Environment mode (`dev` or `production`) | `dev` |
| `LOG_DIR` | Log directory (production mode only) | `./logs` |

### Example

```bash
# Run with custom port
export SERVER_PORT=9090
./gradlew run

# Run with custom configuration
export SERVER_PORT=8888
export SERVER_BACKLOG=200
export SERVER_SHUTDOWN_TIMEOUT_SEC=60
make run-dev
```

## 📋 Development Commands

### Using Gradle

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run the application
./gradlew run

# Clean build artifacts
./gradlew clean

# Format code
./gradlew spotlessApply

# Check code style
./gradlew spotlessCheck checkstyleMain checkstyleTest
```

### Using Make

```bash
# Full pipeline (clean, format, quality check, test, build)
make pipeline

# Run tests
make test

# Run in development mode
make run-dev

# Run in production mode
make run-prod

# Format code
make format

# Check code quality
make quality-check

# Clean
make clean
```

## 🐳 Docker Deployment

### Build and Run with Docker

```bash
# Build Docker image
make docker-build

# Run with docker-compose (recommended)
make docker-up

# View logs
make docker-logs

# Stop container
make docker-down

# Clean up (remove containers and image)
make docker-clean
```

### Docker with Custom Configuration

**Using docker-compose with environment variables:**

```bash
# Set environment variables
export SERVER_PORT=9090
export SERVER_BACKLOG=200
export HOST_PORT=9090

# Run with custom configuration
docker-compose up -d
```

**Using docker run with custom configuration:**

```bash
# Build image
docker build -t java-web-server:latest .

# Run with custom port and configuration
docker run -p 9090:9090 \
  -e SERVER_PORT=9090 \
  -e SERVER_BACKLOG=200 \
  -e SERVER_SHUTDOWN_TIMEOUT_SEC=60 \
  -e ENV=production \
  java-web-server:latest
```

**Docker Image:** ~288MB (Eclipse Temurin 21 JRE)

See [DOCKER.md](DOCKER.md) for detailed Docker deployment guide.

## 📁 Project Structure

```
java-web-server/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/ch/alejandrogarciahub/webserver/
│   │   │   │   └── WebServer.java              # Main application entry point
│   │   │   └── resources/
│   │   │       ├── logback.xml                 # Default logging config
│   │   │       ├── logback-dev.xml             # Development logging (console, DEBUG)
│   │   │       └── logback-production.xml      # Production logging (file, INFO)
│   │   └── test/
│   │       ├── java/                           # Unit tests
│   │       └── resources/                      # Test resources
│   └── build.gradle.kts                        # App module build configuration
├── gradle/                                     # Gradle wrapper
├── Dockerfile                                  # Multi-stage Docker build
├── docker-compose.yml                          # Docker orchestration
├── makefile                                    # Convenient build commands
├── settings.gradle.kts                         # Gradle settings
└── gradle.properties                           # Gradle properties
```

## 🔧 Logging Configuration

The application uses **SLF4J + Logback** with **JSON format** logging.

### Environment-Based Configuration

Logging behavior is controlled by the `ENV` environment variable:

**Development Mode** (`ENV=dev` or default):

- Logs to **console** (stdout)
- **Pretty-printed JSON** for readability
- **DEBUG** level

**Production Mode** (`ENV=production`):

- Logs to **rolling file** (`./logs/application.log`)
- **Compact JSON** (single-line)
- **INFO** level
- Rotation: 100MB/file, 30 days retention

### Logback Files

| File | Purpose |
|------|---------|
| `logback.xml` | Default fallback configuration |
| `logback-dev.xml` | Development: console, DEBUG, pretty JSON |
| `logback-production.xml` | Production: file, INFO, compact JSON |

### Extending Logging

**Add a new logger:**

```xml
<!-- In logback-dev.xml or logback-production.xml -->
<logger name="ch.alejandrogarciahub.webserver.http" level="TRACE" additivity="false">
    <appender-ref ref="CONSOLE" />
</logger>
```

**Customize log format:**

```xml
<!-- Add custom fields to JSON -->
<customFields>{"app":"java-web-server","env":"production","version":"1.0.0"}</customFields>
```

**Change log levels:**

```xml
<!-- Adjust root logger level -->
<root level="WARN">
    <appender-ref ref="CONSOLE" />
</root>
```

See [LOGGING.md](LOGGING.md) for detailed logging documentation.

## 🛠️ Technology Stack

- **Java 21** - Language and runtime
- **Gradle 9.1** - Build system
- **SLF4J 2.0.9** - Logging API
- **Logback 1.4.14** - Logging implementation
- **Logstash Logback Encoder 8.1** - JSON logging
- **JUnit 5** - Testing framework
- **Docker** - Containerization

## 📚 Dependencies

```kotlin
// Logging
implementation("org.slf4j:slf4j-api:2.0.9")
implementation("ch.qos.logback:logback-classic:1.4.14")
implementation("net.logstash.logback:logstash-logback-encoder:8.1")

// Testing
testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
testImplementation("org.mockito:mockito-core:5.8.0")
testImplementation("org.assertj:assertj-core:3.24.2")
```

## 🎯 Project Goals

- ✅ Multi-threaded request handling (Java 21 virtual threads)
- ✅ JSON structured logging
- ✅ Docker containerization
- ⏳ HTTP/1.1 request parsing (TODO)
- ⏳ HTTP/1.1 response generation (TODO)
- ⏳ Static file serving (TODO)
- ⏳ HTTP/1.1 keep-alive support (TODO)
- ⏳ MIME type detection (TODO)

## 📖 Additional Documentation

- [LOGGING.md](LOGGING.md) - Logging configuration and usage
- [DOCKER.md](DOCKER.md) - Docker deployment guide

## 📝 License

This is a learning project for building HTTP servers in Java.
