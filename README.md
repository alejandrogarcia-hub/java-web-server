# Java Web Server

A multi-threaded, file-based HTTP/1.1 web server with thread-pooling and keep-alive support, implemented in Java 21.

## 🚀 Quick Start

### Prerequisites

- **Java 21** (JDK)
- **Gradle 9.1** (build system)
- **Docker** (optional, for containerized deployment)

### Build and Run

```bash
# Build and run full pipeline (clean, format, test, build)
make pipeline

# Run the server (development mode with DEBUG logging)
./gradlew run

# Run in production mode (INFO logging to file)
ENV=production ./gradlew run

# Run with custom port
SERVER_PORT=9090 ./gradlew run
```

### Test

```bash
# Run all tests (163 unit tests)
./gradlew test

# Run specific test class
./gradlew test --tests "ch.alejandrogarciahub.webserver.http.HttpRequestTest"
```

## 🏃 Running the Server

### Method 1: Gradle (Development)

```bash
# Default configuration (port 8080, DEBUG logs to console)
./gradlew run

# With custom port
SERVER_PORT=9090 ./gradlew run

# With custom configuration
SERVER_PORT=8888 DOCUMENT_ROOT=/path/to/files ./gradlew run

# Production mode (INFO logs to ./logs/application.log)
ENV=production ./gradlew run
```

### Method 2: Make Commands

```bash
# Development mode (console logging, DEBUG level)
make run-dev

# Production mode (file logging, INFO level)
make run-prod
```

### Method 3: Docker Compose (Recommended for Production)

```bash
# Build and run with docker-compose
docker-compose up -d

# With custom port
SERVER_PORT=9090 HOST_PORT=9090 docker-compose up -d

# View logs
docker-compose logs -f web-server

# Stop
docker-compose down
```

### Method 4: Docker Run

```bash
# Build image
docker build -t java-web-server:latest .

# Run with default configuration (port 8080)
docker run -d -p 8080:8080 --name web-server java-web-server:latest

# Run with custom port and configuration
docker run -d \
  -p 9090:9090 \
  -e SERVER_PORT=9090 \
  -e SERVER_BACKLOG=200 \
  -e ENV=production \
  -v $(pwd)/logs:/var/log/webserver \
  --name web-server \
  java-web-server:latest

# View logs
docker logs -f web-server

# Stop and remove
docker stop web-server && docker rm web-server
```

### Method 5: JAR File (Direct Execution)

```bash
# Build the JAR
./gradlew build

# Run the JAR directly
java -jar app/build/libs/java-web-server-*.jar

# With custom configuration
SERVER_PORT=9090 ENV=production java -jar app/build/libs/java-web-server-*.jar
```

### Verify Server is Running

```bash
# Test with curl
curl -I http://localhost:8080/

# Expected response:
# HTTP/1.1 404 Not Found
# Content-Type: text/html; charset=UTF-8
# Content-Length: 153
# Date: ...

# Create a test file and serve it
mkdir -p public
echo "<html><body>Hello World</body></html>" > public/index.html
curl http://localhost:8080/

# Test keep-alive with multiple requests
curl -v http://localhost:8080/ http://localhost:8080/

# Check metrics endpoint
curl http://localhost:8080/metrics
```

## ⚙️ Configuration

The server can be configured using environment variables:

### Server Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP listening port | `8080` |
| `SERVER_ACCEPT_TIMEOUT_MS` | Accept loop timeout (milliseconds) | `5000` |
| `SERVER_BACKLOG` | Connection queue size | `100` |
| `SERVER_SHUTDOWN_TIMEOUT_SEC` | Graceful shutdown timeout (seconds) | `30` |
| `CLIENT_READ_TIMEOUT_MS` | Client socket read timeout (milliseconds) | `15000` |
| `ENV` | Environment mode (`dev` or `production`) | `dev` |
| `LOG_DIR` | Log directory (production mode only) | `./logs` |

### HTTP Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `HTTP_MAX_REQUEST_LINE_LENGTH` | Maximum request line length (bytes) | `8192` |
| `HTTP_MAX_HEADER_SIZE` | Maximum total header section size (bytes) | `8192` |
| `HTTP_MAX_HEADERS_COUNT` | Maximum number of header fields | `100` |
| `HTTP_MAX_CONTENT_LENGTH` | Maximum request body size (bytes) | `10485760` (10MB) |
| `DOCUMENT_ROOT` | Document root for serving static files | `./public` |

### Observability Configuration

| Variable | Description | Default |
|----------|-------------|---------|
| `OBS_ACCESS_LOG_ENABLED` | Enable structured HTTP access logs | `true` |
| `OBS_METRICS_ENABLED` | Collect in-memory HTTP metrics | `true` |
| `OBS_METRICS_ENDPOINT_PATH` | Path exposing metrics snapshot | `/metrics` |
| `OBS_PARSE_ERROR_LOGS_PER_MINUTE` | Max parse-error logs per minute (`-1` disables throttling) | `-1` |

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
│   │   │   │   ├── WebServer.java                          # Main entry point & configuration
│   │   │   │   ├── ConnectionHandler.java                  # Interface for connection handling
│   │   │   │   ├── ConnectionHandlerFactory.java           # Factory for thread-safe handlers
│   │   │   │   ├── http/                                   # HTTP protocol layer
│   │   │   │   │   ├── HttpRequest.java                    # Immutable HTTP request
│   │   │   │   │   ├── HttpResponse.java                   # Builder-pattern response
│   │   │   │   │   ├── HttpHeaders.java                    # Case-insensitive headers
│   │   │   │   │   ├── HttpMethod.java                     # HTTP methods enum
│   │   │   │   │   ├── HttpStatus.java                     # HTTP status codes
│   │   │   │   │   └── HttpVersion.java                    # HTTP version enum
│   │   │   │   ├── parser/                                 # HTTP parsing (security boundary)
│   │   │   │   │   ├── HttpRequestParser.java              # RFC 9112 compliant parser
│   │   │   │   │   └── HttpParseException.java             # Parse errors
│   │   │   │   ├── handler/                                # Request handlers
│   │   │   │   │   ├── HttpRequestHandler.java             # Strategy interface
│   │   │   │   │   ├── HttpConnectionHandler.java          # Keep-alive connection loop
│   │   │   │   │   ├── FileServerHandler.java              # Static file serving
│   │   │   │   │   └── MetricsRequestHandler.java          # /metrics endpoint
│   │   │   │   └── observability/                          # Metrics and access logs
│   │   │   │       ├── ObservabilityConfig.java            # Environment configuration
│   │   │   │       ├── HttpMetrics.java                    # Metrics interface
│   │   │   │       ├── HttpMetricsRecorder.java            # Thread-safe metrics impl
│   │   │   │       ├── HttpMetricsSnapshot.java            # Immutable snapshot
│   │   │   │       └── AccessLogger.java                   # Structured access logs
│   │   │   └── resources/
│   │   │       ├── logback.xml                             # Default logging config
│   │   │       ├── logback-dev.xml                         # Development logging
│   │   │       └── logback-production.xml                  # Production logging
│   │   └── test/
│   │       └── java/                                       # 163 unit tests
│   │           ├── http/                                   # HTTP layer tests (90 tests)
│   │           ├── parser/                                 # Parser tests (36 tests)
│   │           ├── handler/                                # Handler tests (35 tests)
│   │           └── observability/                          # Observability tests (2 tests)
│   └── build.gradle.kts                                    # Build configuration
├── config/
│   └── checkstyle/checkstyle.xml                           # Code style rules
├── Dockerfile                                              # Multi-stage Docker build
├── docker-compose.yml                                      # Docker orchestration
├── makefile                                                # Build commands
└── public/                                                 # Document root (default)
```

## 👨‍💻 Developer Guide

### Where to Start Reading the Code

**1. Entry Point & Architecture** (`WebServer.java:175`)

- Virtual thread executor setup
- Environment variable configuration (centralized)
- Factory pattern for thread-safe connection handlers
- Graceful shutdown handling

**2. Connection Lifecycle** (`HttpConnectionHandler.java:40`)

- Keep-alive loop implementation
- HTTP/1.1 vs HTTP/1.0 persistence logic
- Error handling and timeout management
- Handler directive priority over client preferences
- Observability integration (metrics and access logs on every path)

**3. HTTP Parsing** (`HttpRequestParser.java:63`)

- **Security critical**: DoS prevention via configurable limits
- RFC 9112 compliant request line and header parsing
- Graceful EOF handling for HTTP pipelining
- Validation: Host header required for HTTP/1.1

**4. Request/Response Model**

- `HttpRequest.java:28` - Immutable request with keep-alive logic
- `HttpResponse.java:29` - Builder pattern with lazy streaming
- `HttpHeaders.java:17` - Case-insensitive header storage (RFC 9110)

**5. File Serving** (`FileServerHandler.java:42`)

- **Security critical**: Path traversal prevention
- MIME type detection with fallback
- Lazy file streaming to prevent OOM
- Directory index support (index.html)

### Key Design Patterns

**Factory Pattern** - Thread safety via per-connection handler instances

```java
ConnectionHandlerFactory factory = () -> {
    HttpRequestParser parser = new HttpRequestParser(...);
    ObservabilityConfig obsConfig = ObservabilityConfig.fromEnvironment();
    HttpMetricsRecorder metrics = new HttpMetricsRecorder();
    AccessLogger accessLogger = new AccessLogger(obsConfig.isAccessLogEnabled());
    return new HttpConnectionHandler(
        fileHandler, parser, timeout, metrics, obsConfig, accessLogger);
};
```

**Builder Pattern** - Fluent HTTP response construction

```java
HttpResponse response = new HttpResponse()
    .status(HttpStatus.OK)
    .contentType("text/html")
    .keepAlive(true)
    .body("content");
```

**Strategy Pattern** - Pluggable request handlers

```java
interface HttpRequestHandler {
    HttpResponse handle(HttpRequest request) throws IOException;
}
```

**Lazy Evaluation** - Streaming without loading into memory

```java
response.setBodySupplier(() -> Files.newInputStream(file));
```

### Critical Security Boundaries

**1. Parser Limits** (`HttpRequestParser.java:63`)

- Request line: 8KB max (prevents header injection)
- Headers section: 8KB max (prevents DoS)
- Header count: 100 max (prevents hash collision attacks)
- Body size: 10MB max (prevents OOM)

**2. Path Traversal Prevention** (`FileServerHandler.java:146`)

```java
Path resolved = documentRoot.resolve(cleanPath).normalize();
if (!resolved.startsWith(documentRoot)) {
    return null; // Attack detected
}
```

**3. XSS Prevention** (`HttpResponse.java:243`)

- HTML entity escaping in error messages
- Prevents reflected XSS in error pages

### Testing Philosophy

**All 163 tests follow**: *"A test that does not find bugs is a failed test"*

Tests focus on:

- **Security bugs**: Path traversal, DoS, XSS, protocol violations
- **Connection bugs**: Leaks, premature close, version confusion
- **Integration bugs**: Response mixing, parser invocation count, EOF handling

Run tests:

```bash
./gradlew test  # All 163 tests
./gradlew test --tests "*.handler.*"  # Handler layer only
./gradlew test --tests "*.observability.*"  # Observability layer only
```

## 📊 Observability

The server includes comprehensive observability features for monitoring HTTP traffic, performance, and errors.

### Overview

Observability is built into every request path:

- **Access Logs**: Structured JSON logs for every HTTP request (success and failure)
- **Metrics**: In-memory counters and histograms for requests, status codes, latency, and bytes transferred
- **Request Tracing**: X-Request-Id header support for distributed tracing
- **Error Tracking**: Parse errors, timeouts, and I/O failures are logged with full context

### Access Logs

Structured JSON logs emitted for every HTTP request, including:

```json
{
  "@timestamp": "2025-10-02T14:23:45.123Z",
  "logger_name": "ch.alejandrogarciahub.webserver.observability.AccessLogger",
  "level": "INFO",
  "client_address": "/127.0.0.1:54321",
  "method": "GET",
  "path": "/index.html",
  "query_string": "page=1&size=10",
  "http_version": "HTTP/1.1",
  "status": 200,
  "content_length_in": 0,
  "bytes_written": 1234,
  "duration_millis": 15,
  "connection_persistent": true,
  "request_id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Key Features:**

- **Null-safe**: Parse errors and timeouts log with `-` placeholders for missing request fields
- **HEAD request handling**: Correctly logs 0 bytes written (no body) while preserving Content-Length
- **Error scenarios**: Parse errors (400), timeouts (408), and I/O errors (500) all emit access logs
- **Request IDs**: Auto-generated UUID or client-provided `X-Request-Id` for correlation

**Configuration:**

```bash
# Enable/disable access logs (default: true)
OBS_ACCESS_LOG_ENABLED=true

# Throttle parse error logs (default: -1 = unlimited)
OBS_PARSE_ERROR_LOGS_PER_MINUTE=10
```

### Metrics Endpoint

Real-time HTTP metrics available at `/metrics`:

```bash
curl http://localhost:8080/metrics
```

**Response:**

```json
{
  "totalRequests": 1523,
  "activeConnections": 5,
  "totalBytesWritten": 15728640,
  "statusCounts": {
    "SUCCESS": 1450,
    "CLIENT_ERROR": 50,
    "SERVER_ERROR": 23
  },
  "latencyBuckets": {
    "lt_100ms": 1200,
    "lt_500ms": 250,
    "lt_1s": 50,
    "lt_5s": 20,
    "gte_5s": 3
  }
}
```

**Metrics Tracked:**

- **Total Requests**: Count of all HTTP requests (including errors)
- **Active Connections**: Current number of open TCP connections
- **Total Bytes Written**: Cumulative response body bytes sent
- **Status Counts**: Requests grouped by status category (SUCCESS=2xx, REDIRECT=3xx, CLIENT_ERROR=4xx, SERVER_ERROR=5xx)
- **Latency Buckets**: Request duration histogram (<100ms, <500ms, <1s, <5s, ≥5s)

**Configuration:**

```bash
# Enable/disable metrics collection (default: true)
OBS_METRICS_ENABLED=true

# Customize metrics endpoint path (default: /metrics)
OBS_METRICS_ENDPOINT_PATH=/metrics
```

### Request Tracing

The server supports distributed tracing via the `X-Request-Id` header:

**Client-provided ID** (for multi-service tracing):

```bash
curl -H "X-Request-Id: trace-abc-123" http://localhost:8080/
```

**Server-generated ID** (if header not provided):

```bash
curl http://localhost:8080/
# Server generates UUID: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
```

All logs and metrics for a request use the same ID, enabling:

- **End-to-end tracing** across microservices
- **Error correlation** between parse failures and access logs
- **Performance debugging** by request ID

**Implementation Detail:**

Request IDs are seeded *before* parsing to ensure parse errors and timeouts have correlated logs, even when the request object doesn't exist yet.

### Error Observability

Every error path emits complete observability data:

| Error Type | Status | Access Log | Metrics | Request Field |
|------------|--------|------------|---------|---------------|
| Parse error | 400 | ✅ (method=`-`, path=`-`) | ✅ (method=`null`) | ❌ (parsing failed) |
| Socket timeout | 408 | ✅ (method=`-`, path=`-`) | ✅ (method=`null`) | ❌ (timeout before request) |
| I/O error | 500 | ✅ (full request if parsed) | ✅ (method may exist) | ✅/❌ (depends on error timing) |
| Unexpected exception | 500 | ✅ (full request if parsed) | ✅ (method may exist) | ✅/❌ (depends on error timing) |

**Why this matters:**

- **Parse errors** indicate potential attacks or buggy clients
- **Timeouts** reveal slow/malicious clients or network issues
- **I/O errors** during response write suggest client disconnects
- All tracked separately in metrics to distinguish infrastructure vs application failures

### Observability Architecture

**Centralized Pattern:**

All code paths (success and error handlers) call `finalizeObservability()`:

```java
// Success path: emits logs/metrics after response write
finalizeObservability(clientAddress, request, response, durationNanos, requestId);

// Error paths: emits logs/metrics with null request or synthetic response
finalizeObservability(clientAddress, null, response, durationNanos, requestId);
```

**Benefits:**

- Consistent observability across all execution paths
- No code duplication (previously 5 locations)
- Easy to add new observability features (e.g., distributed tracing spans)

**Synthetic Responses:**

For errors where no response is sent to the client (e.g., socket timeout), the server creates a "synthetic response" with just status code and metadata for logging/metrics purposes only.

### Monitoring Best Practices

**1. Track Parse Error Rate:**

High parse error rate indicates:

- DoS attack attempts
- Misconfigured clients
- Protocol implementation bugs

```bash
# Monitor parse errors in logs
grep '"status":400' logs/application.log | wc -l
```

**2. Monitor Timeout Rate:**

High timeout rate suggests:

- Slow clients
- DDoS attacks
- Network issues

```bash
# Check metrics endpoint
curl -s http://localhost:8080/metrics | jq '.statusCounts.CLIENT_ERROR'
```

**3. Track Latency Distribution:**

Most requests should be <100ms for static file serving:

```bash
curl -s http://localhost:8080/metrics | jq '.latencyBuckets'
```

**4. Monitor Active Connections:**

Growing connection count without requests indicates:

- Connection leaks
- Keep-alive timeout issues
- Slowloris attacks

```bash
curl -s http://localhost:8080/metrics | jq '.activeConnections'
```

### Configuration Summary

```bash
# Full observability configuration
export OBS_ACCESS_LOG_ENABLED=true              # Enable access logs
export OBS_METRICS_ENABLED=true                 # Enable metrics collection
export OBS_METRICS_ENDPOINT_PATH=/metrics       # Metrics endpoint path
export OBS_PARSE_ERROR_LOGS_PER_MINUTE=-1      # Throttle parse error logs (-1 = unlimited)
```

### Implementation Files

| File | Purpose |
|------|---------|
| `ObservabilityConfig.java` | Environment variable configuration |
| `HttpMetrics.java` | Metrics interface |
| `HttpMetricsRecorder.java` | Thread-safe in-memory metrics implementation |
| `HttpMetricsSnapshot.java` | Immutable snapshot for `/metrics` endpoint |
| `AccessLogger.java` | Structured JSON access log emitter |
| `MetricsRequestHandler.java` | Handler for `/metrics` endpoint |
| `HttpConnectionHandler.java` | Integration point (calls observability on every path) |

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
- ✅ JSON structured logging (SLF4J + Logback)
- ✅ Docker containerization with multi-stage builds
- ✅ HTTP/1.1 request parsing (RFC 9112 compliant)
- ✅ HTTP/1.1 response generation
- ✅ Static file serving with security (path traversal prevention)
- ✅ HTTP/1.1 keep-alive support (persistent connections)
- ✅ MIME type detection with fallback
- ✅ GET and HEAD methods
- ✅ Thread-safe connection handling (factory pattern)
- ✅ Observability (access logs, metrics endpoint, request tracing)

## 🚧 Future Enhancements

What we haven't tackled yet—none of these are blockers for basic page loads, but they matter for richer browser features:

- **Range / partial responses**: No 206 support, so paused/resumed downloads or video scrubbing won't work.
- **Validation/caching headers**: We always return 200; there's no If-Modified-Since, ETag, Cache-Control, or Date, so browsers re-fetch aggressively.
- **Compression**: No Content-Encoding: gzip/br negotiation, so payloads are larger than they need to be.
- **Chunked transfer**: Every response requires a known Content-Length; dynamic streaming without precomputing length isn't possible yet.
- **Host enforcement**: We accept HTTP/1.1 requests missing the Host header; strictly speaking we should reject them with 400.
- **Error friendliness**: Directory listings, default charset headers for text, and helpful 404 pages could be fleshed out when needed.

## 📖 Additional Documentation

- [LOGGING.md](LOGGING.md) - Logging configuration and usage
- [DOCKER.md](DOCKER.md) - Docker deployment guide
