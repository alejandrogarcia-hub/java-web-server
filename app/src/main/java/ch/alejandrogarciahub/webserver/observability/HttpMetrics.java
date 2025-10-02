package ch.alejandrogarciahub.webserver.observability;

import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpStatus;

/** Minimal interface for recording HTTP server metrics. Implementations should be thread-safe. */
public interface HttpMetrics {

  void connectionOpened();

  void connectionClosed();

  /**
   * Records a completed request.
   *
   * @param method HTTP method (nullable when the request failed before parsing)
   * @param status response status (non-null)
   * @param durationMillis processing time in milliseconds
   * @param bytesWritten bytes written to the client (0 for HEAD / failures)
   */
  void recordRequest(HttpMethod method, HttpStatus status, long durationMillis, long bytesWritten);

  HttpMetricsSnapshot snapshot();
}
