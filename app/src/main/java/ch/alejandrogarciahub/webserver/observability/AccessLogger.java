package ch.alejandrogarciahub.webserver.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured access logger that emits Apache-style key/value entries while preserving MDC
 * correlation IDs supplied by the connection handler.
 */
public final class AccessLogger {

  public record Entry(
      String remoteAddress,
      String method,
      String path,
      String query,
      String httpVersion,
      int status,
      long contentLength,
      long bytesWritten,
      long durationMillis,
      boolean keepAlive,
      String requestId) {}

  private final boolean enabled;
  private final Logger logger;

  /**
   * Creates a logger that writes to the shared {@code http.access} SLF4J channel.
   *
   * @param enabled allows toggling logging without changing the call sites
   */
  public AccessLogger(final boolean enabled) {
    this(enabled, LoggerFactory.getLogger("http.access"));
  }

  /** Package-private constructor for supplying a custom logger (primarily used in tests). */
  AccessLogger(final boolean enabled, final Logger logger) {
    this.enabled = enabled;
    this.logger = logger;
  }

  /**
   * Emits a single structured access log entry when logging is enabled.
   *
   * @param entry immutable record describing the request/response lifecycle
   */
  public void log(final Entry entry) {
    if (!enabled || !logger.isInfoEnabled() || entry == null) {
      return;
    }

    logger.info(
        "remote={} method={} path={} query={} version={} status={} duration_ms={} bytes={} "
            + "content_length={} keep_alive={} request_id={}",
        defaultString(entry.remoteAddress()),
        defaultString(entry.method()),
        defaultString(entry.path()),
        defaultString(entry.query()),
        defaultString(entry.httpVersion()),
        entry.status(),
        entry.durationMillis(),
        entry.bytesWritten(),
        entry.contentLength(),
        entry.keepAlive(),
        defaultString(entry.requestId()));
  }

  private String defaultString(final String value) {
    return value == null ? "-" : value;
  }
}
