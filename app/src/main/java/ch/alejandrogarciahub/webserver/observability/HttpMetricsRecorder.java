package ch.alejandrogarciahub.webserver.observability;

import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * In-memory metrics recorder backed by {@link LongAdder}. Intended for lightweight deployments
 * where exporting metrics happens via snapshot polling.
 */
public final class HttpMetricsRecorder implements HttpMetrics {

  private enum StatusClass {
    SUCCESS,
    CLIENT_ERROR,
    SERVER_ERROR,
    OTHER
  }

  private static final long[] LATENCY_BUCKETS = {100, 500, 1_000};

  private final LongAdder totalRequests = new LongAdder();
  private final LongAdder activeConnections = new LongAdder();
  private final LongAdder bytesSent = new LongAdder();

  private final Map<StatusClass, LongAdder> statusCounters = new EnumMap<>(StatusClass.class);
  private final LongAdder[] latencyCounters =
      new LongAdder[] {new LongAdder(), new LongAdder(), new LongAdder(), new LongAdder()};

  private final Map<HttpMethod, LongAdder> methodCounters = new ConcurrentHashMap<>();

  /** Constructs a new recorder with zeroed counters. */
  public HttpMetricsRecorder() {
    for (final StatusClass statusClass : StatusClass.values()) {
      statusCounters.put(statusClass, new LongAdder());
    }
  }

  @Override
  public void connectionOpened() {
    activeConnections.increment();
  }

  @Override
  public void connectionClosed() {
    activeConnections.decrement();
  }

  @Override
  public void recordRequest(
      final HttpMethod method,
      final HttpStatus status,
      final long durationMillis,
      final long bytesWritten) {
    totalRequests.increment();
    bytesSent.add(bytesWritten);

    if (method != null) {
      methodCounters.computeIfAbsent(method, key -> new LongAdder()).increment();
    }
    classifyStatus(status).increment();
    classifyLatency(durationMillis).increment();
  }

  @Override
  public HttpMetricsSnapshot snapshot() {
    final Map<String, Long> statuses = new HashMap<>();
    statusCounters.forEach(
        (statusClass, counter) -> statuses.put(statusClass.name(), counter.sum()));

    final Map<String, Long> latencies = new HashMap<>();
    latencies.put("lt_100ms", latencyCounters[0].sum());
    latencies.put("lt_500ms", latencyCounters[1].sum());
    latencies.put("lt_1s", latencyCounters[2].sum());
    latencies.put("gte_1s", latencyCounters[3].sum());

    return new HttpMetricsSnapshot(
        totalRequests.sum(), activeConnections.sum(), bytesSent.sum(), statuses, latencies);
  }

  private LongAdder classifyStatus(final HttpStatus status) {
    final int code = status.getCode();
    if (code >= 200 && code < 300) {
      return statusCounters.get(StatusClass.SUCCESS);
    } else if (code >= 400 && code < 500) {
      return statusCounters.get(StatusClass.CLIENT_ERROR);
    } else if (code >= 500 && code < 600) {
      return statusCounters.get(StatusClass.SERVER_ERROR);
    }
    return statusCounters.get(StatusClass.OTHER);
  }

  private LongAdder classifyLatency(final long durationMillis) {
    if (durationMillis < LATENCY_BUCKETS[0]) {
      return latencyCounters[0];
    } else if (durationMillis < LATENCY_BUCKETS[1]) {
      return latencyCounters[1];
    } else if (durationMillis < LATENCY_BUCKETS[2]) {
      return latencyCounters[2];
    }
    return latencyCounters[3];
  }
}
