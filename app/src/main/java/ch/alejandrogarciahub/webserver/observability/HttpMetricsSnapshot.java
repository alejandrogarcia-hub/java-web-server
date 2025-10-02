package ch.alejandrogarciahub.webserver.observability;

import java.util.Map;

/**
 * Immutable snapshot of the metrics counters for export.
 *
 * @param totalRequests cumulative number of completed HTTP requests
 * @param activeConnections currently open connections (gauge)
 * @param bytesSent total bytes written across all responses
 * @param statusCounts counts grouped by status class (e.g., SUCCESS, CLIENT_ERROR)
 * @param latencyBuckets counts grouped by latency bucket (lt_100ms, lt_500ms, etc.)
 */
public record HttpMetricsSnapshot(
    long totalRequests,
    long activeConnections,
    long bytesSent,
    Map<String, Long> statusCounts,
    Map<String, Long> latencyBuckets) {}
