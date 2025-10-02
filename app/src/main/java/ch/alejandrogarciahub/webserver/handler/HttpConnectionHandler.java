package ch.alejandrogarciahub.webserver.handler;

import ch.alejandrogarciahub.webserver.ConnectionHandler;
import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpResponse;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import ch.alejandrogarciahub.webserver.observability.AccessLogger;
import ch.alejandrogarciahub.webserver.observability.HttpMetrics;
import ch.alejandrogarciahub.webserver.observability.ObservabilityConfig;
import ch.alejandrogarciahub.webserver.parser.HttpParseException;
import ch.alejandrogarciahub.webserver.parser.HttpRequestParser;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * HTTP connection handler with keep-alive support.
 *
 * <p>This handler manages the lifecycle of an HTTP connection, including:
 *
 * <ul>
 *   <li>Parsing incoming HTTP requests
 *   <li>Delegating request handling to a {@link HttpRequestHandler}
 *   <li>Managing persistent connections (keep-alive)
 *   <li>Handling errors and generating appropriate error responses
 *   <li>Enforcing read timeouts on client connections
 * </ul>
 *
 * <p><strong>Keep-Alive Behavior:</strong>
 *
 * <ul>
 *   <li>HTTP/1.1: Persistent by default unless {@code Connection: close} is sent
 *   <li>HTTP/1.0: Only persistent if {@code Connection: keep-alive} is sent
 *   <li>Server respects client's connection preference
 *   <li>Connection closed on errors or timeout
 * </ul>
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9112.html#name-persistence">RFC 9112 -
 *     Persistence</a>
 */
public final class HttpConnectionHandler implements ConnectionHandler {
  private static final Logger logger = LoggerFactory.getLogger(HttpConnectionHandler.class);

  private final HttpRequestHandler requestHandler;
  private final HttpRequestParser parser;
  private final int clientReadTimeoutMs;
  private final HttpMetrics metrics;
  private final ObservabilityConfig observabilityConfig;
  private final AccessLogger accessLogger;

  /**
   * Constructs an HttpConnectionHandler with the given request handler, parser, and timeout.
   *
   * @param requestHandler the strategy for handling HTTP requests
   * @param parser the HTTP request parser with configured limits
   * @param clientReadTimeoutMs socket read timeout in milliseconds
   */
  public HttpConnectionHandler(
      final HttpRequestHandler requestHandler,
      final HttpRequestParser parser,
      final int clientReadTimeoutMs,
      final HttpMetrics metrics,
      final ObservabilityConfig observabilityConfig,
      final AccessLogger accessLogger) {
    this.requestHandler = requestHandler;
    this.parser = parser;
    this.clientReadTimeoutMs = clientReadTimeoutMs;
    this.metrics = metrics;
    this.observabilityConfig =
        observabilityConfig != null ? observabilityConfig : ObservabilityConfig.fromEnvironment();
    this.accessLogger = accessLogger;
  }

  /**
   * Handles a client connection with HTTP/1.1 keep-alive support.
   *
   * <p>This method loops to handle multiple requests on a persistent connection until:
   *
   * <ul>
   *   <li>Client sends {@code Connection: close}
   *   <li>Server decides to close (error, timeout, shutdown)
   *   <li>Socket is closed by client
   * </ul>
   *
   * @param clientSocket the client socket connection
   */
  @Override
  public void handle(final Socket clientSocket) {
    final String clientAddress = clientSocket.getRemoteSocketAddress().toString();
    logger.debug("Accepted connection from {}", clientAddress);

    try {
      // Set read timeout to prevent hanging on slow/malicious clients
      clientSocket.setSoTimeout(clientReadTimeoutMs);

      // Shared BufferedInputStream for HTTP pipelining: Wrap the socket's input stream once and
      // reuse it for all pipelined requests on this connection. This prevents buffered data from
      // being stranded if we recreated BufferedInputStream for each request. The parser can safely
      // read multiple requests from the same buffered stream.
      final BufferedInputStream input = new BufferedInputStream(clientSocket.getInputStream());
      final var output = clientSocket.getOutputStream();

      boolean keepAlive = true;
      if (metricsEnabled()) {
        metrics.connectionOpened();
      }

      // Keep-alive loop: handle multiple requests on same connection
      while (keepAlive && !clientSocket.isClosed()) {
        final long startNanos = System.nanoTime();
        // WHY declare as null: Needed in catch blocks for observability; null request = parse fail
        HttpRequest request = null;
        HttpResponse response = null;
        // WHY seed ID before parsing: Correlates logs/metrics even when parsing fails. Replaced
        // with client's X-Request-Id header after successful parse (see ensureRequestId below).
        String requestId = UUID.randomUUID().toString();
        MDC.put("request_id", requestId);

        try {
          // Parse the HTTP request. Graceful EOF: parser.parse() returns null when the
          // client closed the connection cleanly (EOF before next request). In that case
          // we exit the keep-alive loop without treating it as an error.
          request = parser.parse(input);
          if (request == null) {
            keepAlive = false;
            break;
          }

          // WHY replace ID: Prefer client's X-Request-Id for distributed tracing across services
          requestId = ensureRequestId(request, requestId);
          MDC.put("request_id", requestId);

          logger.info(
              "{} {} {} from {}",
              request.getMethod(),
              request.getPath(),
              request.getVersion(),
              clientAddress);

          // Handle the request
          response = requestHandler.handle(request);

          // Set response version to match request version
          response.version(request.getVersion());

          // Handler directive priority: If the handler explicitly set a Connection directive
          // (e.g., forcing close on error, rate limiting), respect it. Otherwise, use the
          // client's request preference (Connection header or HTTP version defaults).
          final boolean handlerHasDirective = response.hasConnectionDirective();
          final boolean finalKeepAlive =
              handlerHasDirective ? response.isConnectionPersistent() : request.isKeepAlive();

          if (!handlerHasDirective) {
            // Only stamp a Connection header if the handler didn't already do so.
            response.keepAlive(finalKeepAlive);
          }
          keepAlive = finalKeepAlive;

          // WHY different write for HEAD: RFC 9110 requires same headers as GET but no body
          if (request.getMethod() == HttpMethod.HEAD) {
            response.writeHeadersOnly(output);
          } else {
            response.writeTo(output);
          }

          final long durationNanos = System.nanoTime() - startNanos;
          logger.debug("Response: {} - Keep-Alive: {}", response, keepAlive);
          // WHY here: Emit metrics/logs after write; duration covers parse + handle + write
          finalizeObservability(clientAddress, request, response, durationNanos, requestId);

        } catch (final HttpParseException e) {
          // WHY close: Malformed request = unclear HTTP state (potential attack)
          logger.warn("Parse error from {}: {}", clientAddress, e.getMessage());
          response = HttpResponse.errorResponse(e.getStatus(), e.getMessage());
          writeResponse(output, clientAddress, response);
          keepAlive = false;
          final long durationNanos = System.nanoTime() - startNanos;
          // WHY null request: Parsing failed, access logs use "-" placeholders
          finalizeObservability(clientAddress, null, response, durationNanos, requestId);

        } catch (final SocketTimeoutException e) {
          // WHY synthetic: Can't write to dead socket, but still need metrics/logs
          logger.debug("Read timeout from {}", clientAddress);
          keepAlive = false;
          response = syntheticResponse(HttpStatus.REQUEST_TIMEOUT);
          final long durationNanos = System.nanoTime() - startNanos;
          finalizeObservability(clientAddress, null, response, durationNanos, requestId);

        } catch (final IOException e) {
          // WHY pass request: I/O error during handle/write, request may exist if parse succeeded
          logger.warn("I/O error from {}: {}", clientAddress, e.getMessage());
          response = HttpResponse.internalServerError();
          writeResponse(output, clientAddress, response);
          keepAlive = false;
          final long durationNanos = System.nanoTime() - startNanos;
          finalizeObservability(clientAddress, request, response, durationNanos, requestId);

        } catch (final Exception e) {
          // WHY catch all: Prevents thread death from unexpected bugs; still emit observability
          logger.error("Unexpected error handling request from {}", clientAddress, e);
          response = HttpResponse.internalServerError();
          writeResponse(output, clientAddress, response);
          keepAlive = false;
          final long durationNanos = System.nanoTime() - startNanos;
          finalizeObservability(clientAddress, request, response, durationNanos, requestId);

        } finally {
          MDC.remove("request_id");
        }
      }

      logger.debug("Connection closed: {}", clientAddress);

    } catch (final IOException e) {
      logger.error("Error setting up connection from {}: {}", clientAddress, e.getMessage());

    } finally {
      MDC.remove("request_id");
      if (metricsEnabled()) {
        metrics.connectionClosed();
      }
      closeSocket(clientSocket, clientAddress);
    }
  }

  /**
   * Writes an HTTP response to the client socket.
   *
   * <p>Helper method for error handling that swallows IOException to prevent cascading errors.
   *
   * @param output the shared output stream for the connection
   * @param response the HTTP response to write
   */
  private void writeResponse(
      final java.io.OutputStream output, final String clientAddress, final HttpResponse response) {
    try {
      response.writeTo(output);
    } catch (final IOException e) {
      logger.error("Failed to write response to {}: {}", clientAddress, e.getMessage());
    }
  }

  /**
   * Closes a socket safely with logging.
   *
   * @param socket the socket to close
   * @param clientAddress the client address for logging
   */
  private void closeSocket(final Socket socket, final String clientAddress) {
    try {
      if (!socket.isClosed()) {
        socket.close();
      }
    } catch (final IOException e) {
      logger.error("Error closing socket for {}: {}", clientAddress, e.getMessage());
    }
  }

  private boolean metricsEnabled() {
    return metrics != null && observabilityConfig.isMetricsEnabled();
  }

  /**
   * Ensures every request has a unique identifier for tracing.
   *
   * <p>WHY this pattern: We generate a UUID before parsing (for error correlation), but prefer
   * client-provided X-Request-Id if present (for distributed tracing across services). This method
   * safely handles null requests (parsing failures) by returning the fallback UUID.
   *
   * @param request the parsed HTTP request (may be null if parsing failed)
   * @param fallbackId the pre-generated UUID to use if request is null or lacks X-Request-Id
   * @return the final request ID to use for observability
   */
  private String ensureRequestId(final HttpRequest request, final String fallbackId) {
    if (request == null) {
      return fallbackId;
    }
    final String header = request.getHeader("X-Request-Id");
    if (header != null && !header.isBlank()) {
      return header;
    }
    return fallbackId;
  }

  /**
   * Emits logging and metrics for a completed request lifecycle (success or failure).
   *
   * <p>WHY centralized method: Previously, observability logic was duplicated across success and
   * error handlers (5 different locations). Centralizing ensures consistent observability coverage
   * for all code paths and makes it easier to add new observability features (e.g., distributed
   * tracing spans).
   *
   * <p>WHY accept null request: Parse errors and timeouts don't have a valid HttpRequest object.
   * Access logs handle this by using "-" placeholders; metrics record null method to track
   * failures.
   *
   * @param request the HTTP request (null if parsing failed)
   * @param response the HTTP response (must not be null)
   */
  private void finalizeObservability(
      final String clientAddress,
      final HttpRequest request,
      final HttpResponse response,
      final long durationNanos,
      final String requestId) {
    if (response == null) {
      return;
    }
    emitAccessLog(clientAddress, request, response, durationNanos, requestId);
    updateMetrics(request, response, durationNanos);
  }

  /**
   * Builds a lightweight response structure used solely for logging/metrics when no payload is
   * emitted (e.g. socket timeouts).
   *
   * <p>WHY synthetic responses: Some failures (socket timeout, client disconnect) prevent us from
   * writing a response back to the client. But we still need to record the event in logs/metrics
   * for monitoring. This creates a minimal HttpResponse with the status code and metadata needed
   * for observability, without attempting network I/O.
   *
   * <p>WHY bodyLength(0L): Indicates no bytes were sent (failure before response write).
   *
   * @param status the HTTP status code representing the failure type
   * @return a non-serialized response object for observability only
   */
  private HttpResponse syntheticResponse(final HttpStatus status) {
    return new HttpResponse().status(status).keepAlive(false).bodyLength(0L);
  }

  private void emitAccessLog(
      final String clientAddress,
      final HttpRequest request,
      final HttpResponse response,
      final long durationNanos,
      final String requestId) {
    if (accessLogger == null || !observabilityConfig.isAccessLogEnabled()) {
      return;
    }

    final long durationMillis = durationNanos / 1_000_000L;
    // WHY null-safe method extraction: Request can be null for parse errors/timeouts. We still
    // log the event but use "-" for method and path (common in access log formats like Apache).
    final HttpMethod method = request != null ? request.getMethod() : null;
    // WHY special handling for HEAD: RFC 9110 requires HEAD responses to omit body but include
    // Content-Length header. We log 0 bytes written for HEAD to accurately reflect network I/O.
    final boolean headRequest = method == HttpMethod.HEAD;

    final AccessLogger.Entry entry =
        new AccessLogger.Entry(
            clientAddress,
            method != null ? method.name() : "-",
            request != null ? request.getPath() : "-",
            request != null ? buildQueryString(request) : null,
            determineHttpVersion(request, response),
            response.getStatus().getCode(),
            request != null ? request.getContentLength() : 0L,
            headRequest ? 0L : response.getBytesWritten(),
            durationMillis,
            response.isConnectionPersistent(),
            requestId);

    accessLogger.log(entry);
  }

  /**
   * Determines HTTP version for access logs.
   *
   * <p>WHY prefer request version: In normal cases, log the version the client used (from the
   * request line). For parse errors where we have no request object, fall back to the response
   * version (which defaults to HTTP/1.1 in error responses).
   */
  private String determineHttpVersion(final HttpRequest request, final HttpResponse response) {
    if (request != null && request.getVersion() != null) {
      return request.getVersion().getValue();
    }
    return response.getVersion().getValue();
  }

  /**
   * Records HTTP metrics for request lifecycle.
   *
   * <p>WHY null method parameter: When parsing fails (parse error, timeout), we have no HttpRequest
   * so method is null. Metrics implementations track this separately (e.g., as "PARSE_ERROR"
   * category) to distinguish infrastructure failures from application-level errors.
   *
   * <p>WHY HEAD request special case: HEAD responses don't include body content, so we record 0
   * bytes written even though Content-Length header may indicate a larger size (what GET would
   * return). This accurately reflects actual network traffic.
   */
  private void updateMetrics(
      final HttpRequest request, final HttpResponse response, final long durationNanos) {
    if (!metricsEnabled() || response == null) {
      return;
    }

    final HttpMethod method = request != null ? request.getMethod() : null;
    final boolean headRequest = method == HttpMethod.HEAD;
    metrics.recordRequest(
        method,
        response.getStatus(),
        durationNanos / 1_000_000L,
        headRequest ? 0L : response.getBytesWritten());
  }

  private String buildQueryString(final HttpRequest request) {
    if (request == null || request.getQueryParams().isEmpty()) {
      return null;
    }
    return request.getQueryParams().entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(Collectors.joining("&"));
  }
}
