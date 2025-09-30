# Java Web Server

A multi-threaded, file-based HTTP/1.1 web server with thread-pooling and keep-alive support, implemented in Java 21.

## 🚀 Quick Start

### Prerequisites

- **Java 21** (JDK)
- **Gradle 9.1** (build system)
- **Docker** (optional, for containerized deployment)

### Build and Run

```bash
# Build the project
./gradlew build

# Run in development mode (DEBUG logs to console)
make run-dev

# Run in production mode (INFO logs to file)
make run-prod
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
```

### Using Make

```bash
# Build and run
make pipeline

# Run tests
make test

# Run in development mode
make run-dev

# Run in production mode
make run-prod

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

### Manual Docker Commands

```bash
# Build image
docker build -t java-web-server:latest .

# Run container
docker run -p 8080:8080 -e ENV=production java-web-server:latest

# Run with docker-compose
docker-compose up -d
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

- ✅ Multi-threaded request handling
- ✅ Thread pooling for efficient resource management
- ✅ HTTP/1.1 keep-alive support
- ✅ File-based static content serving
- ✅ JSON structured logging
- ✅ Docker containerization
- ⏳ HTTP request parsing (TODO)
- ⏳ HTTP response generation (TODO)
- ⏳ MIME type detection (TODO)
- ⏳ Connection management (TODO)

## 📖 Additional Documentation

- [LOGGING.md](LOGGING.md) - Logging configuration and usage
- [DOCKER.md](DOCKER.md) - Docker deployment guide

## 📝 License

This is a learning project for building HTTP servers in Java.
