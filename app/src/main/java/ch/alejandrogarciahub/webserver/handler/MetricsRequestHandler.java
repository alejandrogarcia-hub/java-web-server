package ch.alejandrogarciahub.webserver.handler;

import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpResponse;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import ch.alejandrogarciahub.webserver.observability.HttpMetrics;
import ch.alejandrogarciahub.webserver.observability.HttpMetricsSnapshot;
import java.io.IOException;
import java.util.Map;

/** Serves a JSON representation of the current {@link HttpMetrics} snapshot. */
public final class MetricsRequestHandler implements HttpRequestHandler {

  private final HttpMetrics metrics;

  public MetricsRequestHandler(final HttpMetrics metrics) {
    this.metrics = metrics;
  }

  @Override
  public HttpResponse handle(final HttpRequest request) throws IOException {
    if (request.getMethod() != HttpMethod.GET) {
      return HttpResponse.methodNotAllowed("GET");
    }

    final HttpMetricsSnapshot snapshot = metrics != null ? metrics.snapshot() : emptySnapshot();
    final String json = toJson(snapshot);

    return new HttpResponse()
        .status(HttpStatus.OK)
        .contentType("application/json; charset=UTF-8")
        .keepAlive(true)
        .body(json);
  }

  private HttpMetricsSnapshot emptySnapshot() {
    return new HttpMetricsSnapshot(0, 0, 0, Map.of(), Map.of());
  }

  private String toJson(final HttpMetricsSnapshot snapshot) {
    final StringBuilder builder = new StringBuilder(256);
    builder.append('{');
    builder.append("\"totalRequests\":").append(snapshot.totalRequests()).append(',');
    builder.append("\"activeConnections\":").append(snapshot.activeConnections()).append(',');
    builder.append("\"bytesSent\":").append(snapshot.bytesSent()).append(',');
    builder.append("\"statusCounts\":");
    appendMap(builder, snapshot.statusCounts());
    builder.append(',');
    builder.append("\"latencyBuckets\":");
    appendMap(builder, snapshot.latencyBuckets());
    builder.append('}');
    return builder.toString();
  }

  private void appendMap(final StringBuilder builder, final Map<String, Long> map) {
    builder.append('{');
    boolean first = true;
    for (final Map.Entry<String, Long> entry : map.entrySet()) {
      if (!first) {
        builder.append(',');
      }
      first = false;
      builder.append('"').append(entry.getKey()).append('"').append(':').append(entry.getValue());
    }
    builder.append('}');
  }
}
