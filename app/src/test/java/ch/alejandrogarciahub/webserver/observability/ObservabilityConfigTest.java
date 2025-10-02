package ch.alejandrogarciahub.webserver.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObservabilityConfigTest {

  @Test
  void shouldExposeConfiguredValues() {
    final ObservabilityConfig config = new ObservabilityConfig(true, false, 42, "/metrics-test");

    assertThat(config.isAccessLogEnabled()).isTrue();
    assertThat(config.isMetricsEnabled()).isFalse();
    assertThat(config.getMaxParseErrorLogsPerMinute()).isEqualTo(42);
    assertThat(config.getMetricsEndpointPath()).isEqualTo("/metrics-test");
  }
}
