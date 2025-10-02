package ch.alejandrogarciahub.webserver.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import org.junit.jupiter.api.Test;

class HttpMetricsRecorderTest {

  @Test
  void shouldTrackConnectionsAndRequests() {
    final HttpMetricsRecorder recorder = new HttpMetricsRecorder();

    recorder.connectionOpened();
    recorder.connectionOpened();
    recorder.recordRequest(HttpMethod.GET, HttpStatus.OK, 42, 512);
    recorder.recordRequest(HttpMethod.GET, HttpStatus.BAD_REQUEST, 250, 128);
    recorder.connectionClosed();

    final HttpMetricsSnapshot snapshot = recorder.snapshot();

    assertThat(snapshot.totalRequests()).isEqualTo(2);
    assertThat(snapshot.activeConnections()).isEqualTo(1);
    assertThat(snapshot.bytesSent()).isEqualTo(640);
    assertThat(snapshot.statusCounts().get("SUCCESS")).isEqualTo(1);
    assertThat(snapshot.statusCounts().get("CLIENT_ERROR")).isEqualTo(1);
    assertThat(snapshot.latencyBuckets().get("lt_500ms")).isGreaterThan(0);
  }
}
