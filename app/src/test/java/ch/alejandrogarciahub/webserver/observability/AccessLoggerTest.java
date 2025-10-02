package ch.alejandrogarciahub.webserver.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class AccessLoggerTest {

  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setup() {
    logger = (Logger) LoggerFactory.getLogger("test.http.access");
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @Test
  void shouldEmitStructuredLogLine() {
    final AccessLogger accessLogger = new AccessLogger(true, logger);
    accessLogger.log(
        new AccessLogger.Entry(
            "127.0.0.1:1234",
            "GET",
            "/index.html",
            "id=42",
            "HTTP/1.1",
            200,
            512,
            256,
            12,
            true,
            "req-1"));

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
        .contains("method=GET")
        .contains("path=/index.html")
        .contains("status=200")
        .contains("request_id=req-1");
  }

  @Test
  void shouldSkipLoggingWhenDisabled() {
    final AccessLogger accessLogger = new AccessLogger(false, logger);
    accessLogger.log(
        new AccessLogger.Entry(
            "127.0.0.1", "GET", "/", null, "HTTP/1.1", 200, 0, 0, 0, true, null));

    assertThat(appender.list).isEmpty();
  }
}
