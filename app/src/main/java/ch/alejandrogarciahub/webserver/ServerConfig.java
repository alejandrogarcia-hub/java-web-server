package ch.alejandrogarciahub.webserver;

import ch.alejandrogarciahub.webserver.observability.AccessLogger;
import ch.alejandrogarciahub.webserver.observability.HttpMetrics;
import ch.alejandrogarciahub.webserver.observability.ObservabilityConfig;
import java.util.Objects;

/**
 * Immutable configuration for the web server.
 *
 * <p>Holds all server configuration parameters to avoid excessive constructor parameters.
 */
final class ServerConfig {
  private final int port;
  private final int acceptTimeoutMs;
  private final int backlog;
  private final int shutdownTimeoutSeconds;
  private final int clientReadTimeoutMs;
  private final ConnectionHandlerFactory connectionHandlerFactory;
  private final ObservabilityConfig observabilityConfig;
  private final HttpMetrics metrics;
  private final AccessLogger accessLogger;

  private ServerConfig(final Builder builder) {
    this.port = builder.port;
    this.acceptTimeoutMs = builder.acceptTimeoutMs;
    this.backlog = builder.backlog;
    this.shutdownTimeoutSeconds = builder.shutdownTimeoutSeconds;
    this.clientReadTimeoutMs = builder.clientReadTimeoutMs;
    this.connectionHandlerFactory =
        Objects.requireNonNull(builder.connectionHandlerFactory, "connectionHandlerFactory");
    this.observabilityConfig = builder.observabilityConfig;
    this.metrics = builder.metrics;
    this.accessLogger = builder.accessLogger;
  }

  static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a test configuration with minimal observability.
   *
   * @param port server port
   * @param acceptTimeoutMs accept timeout
   * @param backlog connection backlog
   * @param shutdownTimeoutSeconds shutdown timeout
   * @param clientReadTimeoutMs client read timeout
   * @param connectionHandlerFactory connection handler factory
   * @return server configuration for testing
   */
  static ServerConfig forTesting(
      final int port,
      final int acceptTimeoutMs,
      final int backlog,
      final int shutdownTimeoutSeconds,
      final int clientReadTimeoutMs,
      final ConnectionHandlerFactory connectionHandlerFactory) {
    return builder()
        .port(port)
        .acceptTimeoutMs(acceptTimeoutMs)
        .backlog(backlog)
        .shutdownTimeoutSeconds(shutdownTimeoutSeconds)
        .clientReadTimeoutMs(clientReadTimeoutMs)
        .connectionHandlerFactory(connectionHandlerFactory)
        .observabilityConfig(new ObservabilityConfig(false, false, -1, "/metrics"))
        .metrics(null)
        .accessLogger(new AccessLogger(false))
        .build();
  }

  static final class Builder {
    private int port;
    private int acceptTimeoutMs;
    private int backlog;
    private int shutdownTimeoutSeconds;
    private int clientReadTimeoutMs;
    private ConnectionHandlerFactory connectionHandlerFactory;
    private ObservabilityConfig observabilityConfig;
    private HttpMetrics metrics;
    private AccessLogger accessLogger;

    Builder port(final int port) {
      this.port = port;
      return this;
    }

    Builder acceptTimeoutMs(final int acceptTimeoutMs) {
      this.acceptTimeoutMs = acceptTimeoutMs;
      return this;
    }

    Builder backlog(final int backlog) {
      this.backlog = backlog;
      return this;
    }

    Builder shutdownTimeoutSeconds(final int shutdownTimeoutSeconds) {
      this.shutdownTimeoutSeconds = shutdownTimeoutSeconds;
      return this;
    }

    Builder clientReadTimeoutMs(final int clientReadTimeoutMs) {
      this.clientReadTimeoutMs = clientReadTimeoutMs;
      return this;
    }

    Builder connectionHandlerFactory(final ConnectionHandlerFactory connectionHandlerFactory) {
      this.connectionHandlerFactory = connectionHandlerFactory;
      return this;
    }

    Builder observabilityConfig(final ObservabilityConfig observabilityConfig) {
      this.observabilityConfig = observabilityConfig;
      return this;
    }

    Builder metrics(final HttpMetrics metrics) {
      this.metrics = metrics;
      return this;
    }

    Builder accessLogger(final AccessLogger accessLogger) {
      this.accessLogger = accessLogger;
      return this;
    }

    ServerConfig build() {
      return new ServerConfig(this);
    }
  }

  int getPort() {
    return port;
  }

  int getAcceptTimeoutMs() {
    return acceptTimeoutMs;
  }

  int getBacklog() {
    return backlog;
  }

  int getShutdownTimeoutSeconds() {
    return shutdownTimeoutSeconds;
  }

  int getClientReadTimeoutMs() {
    return clientReadTimeoutMs;
  }

  ConnectionHandlerFactory getConnectionHandlerFactory() {
    return connectionHandlerFactory;
  }

  ObservabilityConfig getObservabilityConfig() {
    return observabilityConfig;
  }

  HttpMetrics getMetrics() {
    return metrics;
  }

  AccessLogger getAccessLogger() {
    return accessLogger;
  }
}
