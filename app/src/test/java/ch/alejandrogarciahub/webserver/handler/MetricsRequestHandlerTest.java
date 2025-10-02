package ch.alejandrogarciahub.webserver.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpResponse;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import ch.alejandrogarciahub.webserver.observability.HttpMetrics;
import ch.alejandrogarciahub.webserver.observability.HttpMetricsSnapshot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricsRequestHandlerTest {

  @Test
  void shouldReturnSnapshotAsJson() throws IOException {
    final HttpMetrics metrics = mock(HttpMetrics.class);
    when(metrics.snapshot())
        .thenReturn(
            new HttpMetricsSnapshot(
                5, 1, 1024, Map.of("SUCCESS", 4L, "CLIENT_ERROR", 1L), Map.of("lt_100ms", 5L)));

    final MetricsRequestHandler handler = new MetricsRequestHandler(metrics);
    final HttpRequest request = mock(HttpRequest.class);
    when(request.getMethod()).thenReturn(HttpMethod.GET);

    final HttpResponse response = handler.handle(request);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);
    final String body = writeBody(response);
    assertThat(body).contains("\"totalRequests\":5");
    assertThat(body).contains("\"statusCounts\":{");
  }

  @Test
  void shouldRejectNonGetMethods() throws IOException {
    final MetricsRequestHandler handler = new MetricsRequestHandler(null);
    final HttpRequest request = mock(HttpRequest.class);
    when(request.getMethod()).thenReturn(HttpMethod.POST);

    final HttpResponse response = handler.handle(request);

    assertThat(response.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  private String writeBody(final HttpResponse response) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      response.writeTo(out);
      final String output = out.toString();
      final int bodyStart = output.indexOf("\r\n\r\n");
      if (bodyStart < 0) {
        return output;
      }
      return output.substring(bodyStart + 4);
    }
  }
}
