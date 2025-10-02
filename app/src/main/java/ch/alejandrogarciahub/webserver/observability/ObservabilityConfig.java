package ch.alejandrogarciahub.webserver.observability;

/**
 * Central configuration for observability features such as access logging and HTTP metrics.
 *
 * <p>The configuration is designed to be inexpensive to read and immutable so it can be shared
 * safely across threads.
 */
public final class ObservabilityConfig {

  private final boolean accessLogEnabled;
  private final boolean metricsEnabled;
  private final int maxParseErrorLogsPerMinute;
  private final String metricsEndpointPath;

  /**
   * Creates a configuration snapshot.
   *
   * @param accessLogEnabled whether access logging should be emitted
   * @param metricsEnabled whether HTTP metrics should be collected
   * @param maxParseErrorLogsPerMinute throttle for parse-error logging (-1 disables)
   */
  public ObservabilityConfig(
      final boolean accessLogEnabled,
      final boolean metricsEnabled,
      final int maxParseErrorLogsPerMinute,
      final String metricsEndpointPath) {
    this.accessLogEnabled = accessLogEnabled;
    this.metricsEnabled = metricsEnabled;
    this.maxParseErrorLogsPerMinute = maxParseErrorLogsPerMinute;
    this.metricsEndpointPath =
        metricsEndpointPath != null && !metricsEndpointPath.isBlank()
            ? metricsEndpointPath
            : "/metrics";
  }

  /** Creates a config snapshot by reading environment variables. */
  public static ObservabilityConfig fromEnvironment() {
    final boolean accessLogEnabled = readBooleanEnv("OBS_ACCESS_LOG_ENABLED", true);
    final boolean metricsEnabled = readBooleanEnv("OBS_METRICS_ENABLED", true);
    final int maxParseErrorLogsPerMinute = readIntEnv("OBS_PARSE_ERROR_LOGS_PER_MINUTE", -1);
    final String metricsEndpointPath =
        System.getenv().getOrDefault("OBS_METRICS_ENDPOINT_PATH", "/metrics");

    return new ObservabilityConfig(
        accessLogEnabled, metricsEnabled, maxParseErrorLogsPerMinute, metricsEndpointPath);
  }

  public boolean isAccessLogEnabled() {
    return accessLogEnabled;
  }

  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  /**
   * @return maximum number of parse-error log entries per minute (-1 disables sampling)
   */
  public int getMaxParseErrorLogsPerMinute() {
    return maxParseErrorLogsPerMinute;
  }

  /**
   * @return HTTP path that should expose metrics (e.g., "/metrics").
   */
  public String getMetricsEndpointPath() {
    return metricsEndpointPath;
  }

  private static boolean readBooleanEnv(final String key, final boolean defaultValue) {
    final String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return value.equalsIgnoreCase("true")
        || value.equalsIgnoreCase("1")
        || value.equalsIgnoreCase("yes");
  }

  private static int readIntEnv(final String key, final int defaultValue) {
    final String value = System.getenv(key);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (final NumberFormatException ignore) {
      return defaultValue;
    }
  }
}
