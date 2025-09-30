# Logging Configuration

This project uses **SLF4J + Logback** with **JSON format** logging via `logstash-logback-encoder`.

## Environment-Based Configuration

Logging behavior is controlled by the `ENV` environment variable using Spring Profiles approach:

### Development Mode (Default)
```bash
ENV=dev ./gradlew run
# or simply:
./gradlew run
```

**Behavior:**
- Logs to **stdout/console**
- **Pretty-printed JSON** for readability
- **DEBUG** level logging
- Includes MDC (correlation IDs)

**Example output:**
```json
{
  "@timestamp" : "2025-09-30T15:44:26.433+02:00",
  "@version" : "1",
  "message" : "Starting Java Web Server",
  "logger_name" : "ch.alejandrogarciahub.webserver.WebServer",
  "thread_name" : "main",
  "level" : "INFO",
  "level_value" : 20000,
  "app" : "java-web-server",
  "env" : "dev"
}
```

### Production Mode
```bash
ENV=production ./gradlew run
```

**Behavior:**
- Logs to **rolling file** (`./logs/application.log` or `app/logs/application.log`)
- **Compact JSON** (single line per log)
- **INFO** level logging
- Includes MDC (correlation IDs)
- **Log rotation:**
  - Max 100MB per file
  - 30 days retention
  - 10GB total cap
  - Daily rollover pattern: `application.YYYY-MM-DD.N.log`

**Example output:**
```json
{"@timestamp":"2025-09-30T15:44:42.016+02:00","@version":"1","message":"Starting Java Web Server","logger_name":"ch.alejandrogarciahub.webserver.WebServer","thread_name":"main","level":"INFO","level_value":20000,"app":"java-web-server","env":"production"}
```

## Configuration Files

- `logback.xml` - Default fallback configuration
- `logback-dev.xml` - Development environment (console, DEBUG, pretty JSON)
- `logback-production.xml` - Production environment (file, INFO, compact JSON)

Configuration is automatically selected based on `ENV` variable via static initializer in `WebServer.java`.

## Docker Usage

When running in Docker, set the environment variable:

```dockerfile
ENV ENV=production
# Optional: customize log directory
ENV LOG_DIR=/var/log/webserver
```

Or via docker-compose:
```yaml
environment:
  - ENV=production
  - LOG_DIR=/var/log/webserver
```

## Using Loggers in Code

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class MyClass {
    private static final Logger logger = LoggerFactory.getLogger(MyClass.class);

    public void myMethod() {
        // Basic logging
        logger.info("This is an info message");
        logger.debug("Debug with parameter: {}", someValue);
        logger.error("Error occurred", exception);

        // Using MDC for correlation IDs
        MDC.put("requestId", "req-123");
        MDC.put("userId", "user-456");
        logger.info("Processing request");
        MDC.clear(); // Always clear after use
    }
}
```

## JSON Log Fields

Each log entry includes:
- `@timestamp` - ISO 8601 format timestamp
- `@version` - Logstash version
- `message` - Log message
- `logger_name` - Fully qualified logger name
- `thread_name` - Thread that created the log
- `level` - Log level (TRACE, DEBUG, INFO, WARN, ERROR)
- `level_value` - Numeric log level
- `app` - Application name ("java-web-server")
- `env` - Environment ("dev", "production")
- `stack_trace` - Exception stack trace (if present)
- MDC fields (if set, e.g., `requestId`, `userId`)

## Manual Configuration Override

To use a specific configuration file:
```bash
./gradlew run -Dlogback.configurationFile=logback-production.xml
```

## Dependencies

```gradle
implementation("org.slf4j:slf4j-api:2.0.9")
implementation("ch.qos.logback:logback-classic:1.4.14")
implementation("net.logstash.logback:logstash-logback-encoder:8.1")
```