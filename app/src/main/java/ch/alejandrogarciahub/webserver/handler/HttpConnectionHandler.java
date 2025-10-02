package ch.alejandrogarciahub.webserver.handler;

import ch.alejandrogarciahub.webserver.ConnectionHandler;
import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpResponse;
import ch.alejandrogarciahub.webserver.parser.HttpParseException;
import ch.alejandrogarciahub.webserver.parser.HttpRequestParser;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
      final int clientReadTimeoutMs) {
    this.requestHandler = requestHandler;
    this.parser = parser;
    this.clientReadTimeoutMs = clientReadTimeoutMs;
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

      // Keep-alive loop: handle multiple requests on same connection
      while (keepAlive && !clientSocket.isClosed()) {
        try {
          // Parse the HTTP request
          // Graceful EOF: parser.parse() returns null when client closes connection cleanly
          // (EOF before next request starts). This is normal for HTTP pipelining end.
          final HttpRequest request = parser.parse(input);
          if (request == null) {
            // Client closed the socket without a further request; end keep-alive quietly.
            keepAlive = false;
            break;
          }

          logger.info(
              "{} {} {} from {}",
              request.getMethod(),
              request.getPath(),
              request.getVersion(),
              clientAddress);

          // Handle the request
          final HttpResponse response = requestHandler.handle(request);

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

          // Write response based on method
          if (request.getMethod().name().equals("HEAD")) {
            response.writeHeadersOnly(output);
          } else {
            response.writeTo(output);
          }

          logger.debug("Response: {} - Keep-Alive: {}", response, keepAlive);

        } catch (final HttpParseException e) {
          // HTTP parsing error - send error response and close connection
          logger.warn("Parse error from {}: {}", clientAddress, e.getMessage());
          writeResponse(
              output, clientAddress, HttpResponse.errorResponse(e.getStatus(), e.getMessage()));
          keepAlive = false; // Close connection on parse errors

        } catch (final SocketTimeoutException e) {
          // Client read timeout - close connection
          logger.debug("Read timeout from {}", clientAddress);
          keepAlive = false;

        } catch (final IOException e) {
          // I/O error during request/response - send 500 and close connection
          logger.warn("I/O error from {}: {}", clientAddress, e.getMessage());
          writeResponse(output, clientAddress, HttpResponse.internalServerError());
          keepAlive = false;

        } catch (final Exception e) {
          // Unexpected error - send 500 and close connection
          logger.error("Unexpected error handling request from {}", clientAddress, e);
          writeResponse(output, clientAddress, HttpResponse.internalServerError());
          keepAlive = false;
        }
      }

      logger.debug("Connection closed: {}", clientAddress);

    } catch (final IOException e) {
      logger.error("Error setting up connection from {}: {}", clientAddress, e.getMessage());

    } finally {
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
}
