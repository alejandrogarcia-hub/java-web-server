package ch.alejandrogarciahub.webserver.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Builder for constructing HTTP/1.1 responses.
 *
 * <p>This class uses the builder pattern to construct HTTP responses with fluent method chaining.
 * Responses can be written directly to an output stream.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9112.html">RFC 9112 - HTTP/1.1</a>
 */
public final class HttpResponse {
  private HttpStatus status = HttpStatus.OK;
  private HttpVersion version = HttpVersion.HTTP_1_1;
  private final HttpHeaders headers = new HttpHeaders();
  private byte[] body = new byte[0];

  // Lazy body streaming: Allows serving large files without loading them entirely into memory.
  // The supplier is called when writeTo() is invoked, enabling efficient streaming.
  private InputStreamSupplier bodySupplier;

  // Connection directive tracking: These fields allow the handler to explicitly control
  // connection persistence, overriding default HTTP version behavior when needed
  // (e.g., forcing close on errors, rate limiting, resource exhaustion).
  private boolean connectionHeaderSet = false;
  private Boolean explicitPersistent; // null when not explicitly set

  /** Constructs an HttpResponse with default values (200 OK, HTTP/1.1). */
  public HttpResponse() {
    // Set default headers
    headers.set("Server", "Java-WebServer/1.0");
    // Note: Connection header is NOT set by default
    // HTTP/1.1: persistent by default, only set "Connection: close" when closing
    // HTTP/1.0: non-persistent by default, set "Connection: keep-alive" to persist
  }

  /**
   * Sets the HTTP status.
   *
   * @param status the HTTP status
   * @return this HttpResponse for method chaining
   */
  public HttpResponse status(final HttpStatus status) {
    this.status = status;
    return this;
  }

  /** Returns the current HTTP status for inspection (e.g., observability hooks). */
  public HttpStatus getStatus() {
    return status;
  }

  /**
   * Sets the HTTP version.
   *
   * @param version the HTTP version
   * @return this HttpResponse for method chaining
   */
  public HttpResponse version(final HttpVersion version) {
    this.version = version;
    return this;
  }

  /** Returns the HTTP version the response is configured for. */
  public HttpVersion getVersion() {
    return version;
  }

  /**
   * Sets a header field.
   *
   * @param name the header name
   * @param value the header value
   * @return this HttpResponse for method chaining
   */
  public HttpResponse header(final String name, final String value) {
    headers.set(name, value);
    if ("Connection".equalsIgnoreCase(name)) {
      // Track explicit handler intent so the connection loop can respect it.
      connectionHeaderSet = true;
      explicitPersistent = !"close".equalsIgnoreCase(value);
    }
    return this;
  }

  /**
   * Sets the response body.
   *
   * <p>Automatically sets the Content-Length header.
   *
   * @param body the response body bytes
   * @return this HttpResponse for method chaining
   */
  public HttpResponse body(final byte[] body) {
    this.body = body.clone(); // Defensive copy
    headers.set("Content-Length", String.valueOf(body.length));
    this.bodySupplier = null;
    return this;
  }

  /**
   * Sets the response body from a string.
   *
   * <p>Encodes the string as UTF-8 and sets Content-Length.
   *
   * @param body the response body string
   * @return this HttpResponse for method chaining
   */
  public HttpResponse body(final String body) {
    return body(body.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Declares the response body length without materialising the payload. Callers must also provide
   * a {@link #setBodySupplier(java.util.function.Supplier)} to stream the content when the response
   * is written.
   *
   * <p>This enables memory-efficient serving of large files by setting Content-Length header while
   * keeping the body empty. The actual content is streamed later via the InputStreamSupplier,
   * preventing OutOfMemoryError when serving large files concurrently.
   */
  public HttpResponse bodyLength(final long length) {
    headers.set("Content-Length", String.valueOf(length));
    this.body = new byte[0];
    return this;
  }

  /**
   * Provides a supplier that opens a fresh InputStream each time the response body needs to be
   * written. Consumers are responsible for closing the stream.
   *
   * <p>This supplier-based approach allows lazy evaluation - the file is only opened when writeTo()
   * is invoked, not when the response is built. This is critical for serving static files
   * efficiently without loading entire files into heap memory.
   */
  public HttpResponse setBodySupplier(final InputStreamSupplier supplier) {
    this.bodySupplier = supplier;
    return this;
  }

  /**
   * Sets the Connection header based on HTTP version and keep-alive decision.
   *
   * <p><strong>HTTP/1.1 (RFC 9112 Section 9.6):</strong>
   *
   * <ul>
   *   <li>Persistent connections by default
   *   <li>Only send {@code Connection: close} when closing
   *   <li>Do NOT send {@code Connection: keep-alive} (deprecated)
   * </ul>
   *
   * <p><strong>HTTP/1.0 (RFC 1945 Section 8.1):</strong>
   *
   * <ul>
   *   <li>Non-persistent connections by default
   *   <li>Send {@code Connection: keep-alive} to persist
   *   <li>Send {@code Connection: close} when closing (explicit)
   * </ul>
   *
   * @param keepAlive true to keep connection alive, false to close
   * @return this HttpResponse for method chaining
   */
  public HttpResponse keepAlive(final boolean keepAlive) {
    if (version == HttpVersion.HTTP_1_1) {
      // HTTP/1.1: Only set header when closing
      if (!keepAlive) {
        headers.set("Connection", "close");
        connectionHeaderSet = true;
        explicitPersistent = false;
      } else {
        // Remove Connection header - persistent is default
        headers.remove("Connection");
        connectionHeaderSet = false;
        explicitPersistent = null;
      }
    } else {
      // HTTP/1.0: Explicitly set header for both cases
      headers.set("Connection", keepAlive ? "keep-alive" : "close");
      connectionHeaderSet = true;
      explicitPersistent = keepAlive;
    }
    return this;
  }

  /**
   * Sets the Content-Type header.
   *
   * @param contentType the MIME type (e.g., "text/html; charset=UTF-8")
   * @return this HttpResponse for method chaining
   */
  public HttpResponse contentType(final String contentType) {
    headers.set("Content-Type", contentType);
    return this;
  }

  /**
   * Writes the complete HTTP response to an output stream.
   *
   * <p>Format: Status-Line CRLF *(Header-Field CRLF) CRLF [Message-Body]
   *
   * @param output the output stream to write to
   * @throws IOException if an I/O error occurs
   */
  public void writeTo(final OutputStream output) throws IOException {
    final PrintWriter writer =
        new PrintWriter(new OutputStreamWriter(output, StandardCharsets.ISO_8859_1), false);

    // Status line: HTTP-Version SP Status-Code SP Reason-Phrase CRLF
    writer.print(version.getValue());
    writer.print(" ");
    writer.print(status.getCode());
    writer.print(" ");
    writer.print(status.getReasonPhrase());
    writer.print("\r\n");

    // Header fields
    for (final String name : headers.names()) {
      writer.print(name);
      writer.print(": ");
      writer.print(headers.get(name));
      writer.print("\r\n");
    }

    // Empty line separating headers from body
    writer.print("\r\n");
    writer.flush();

    // Message body (if present)
    // Lazy streaming: When bodySupplier is set, the file is opened here (not when response was
    // built), allowing efficient streaming of large files without loading into memory.
    if (bodySupplier != null) {
      try (InputStream stream = bodySupplier.get()) {
        stream.transferTo(output);
      }
      output.flush();
    } else if (body.length > 0) {
      output.write(body);
      output.flush();
    }
  }

  /**
   * Returns true if the response already contains a Connection header set explicitly by the
   * handler.
   *
   * <p>This allows the connection loop to distinguish between handler intent (explicit close for
   * error handling, rate limiting, etc.) versus default HTTP version behavior. When a handler
   * explicitly sets a Connection directive, it should take priority over protocol defaults.
   */
  public boolean hasConnectionDirective() {
    return connectionHeaderSet;
  }

  /**
   * Determines whether the current Connection directive (if any) keeps the connection alive. Falls
   * back to the HTTP version defaults when no explicit directive was set.
   *
   * <p>The three-state logic (true/false/null) allows handlers to override HTTP defaults when
   * needed. For example, error responses can force connection close regardless of HTTP version,
   * while normal responses use protocol defaults (HTTP/1.1 persistent, HTTP/1.0 non-persistent).
   */
  public boolean isConnectionPersistent() {
    if (explicitPersistent != null) {
      return explicitPersistent;
    }
    return version.defaultsToKeepAlive();
  }

  /** Returns the declared number of bytes that will be written in the body. */
  public long getDeclaredContentLength() {
    final String value = headers.get("Content-Length");
    if (value == null) {
      return 0L;
    }
    try {
      return Long.parseLong(value);
    } catch (final NumberFormatException ignore) {
      return 0L;
    }
  }

  /** Convenience accessor for observability hooks to report body size. */
  public long getBytesWritten() {
    return getDeclaredContentLength();
  }

  /**
   * Writes the response without a body (for HEAD requests).
   *
   * <p>This method writes only the status line and headers, omitting the message body. The
   * Content-Length header (if set) indicates what the body size would be for a GET request.
   *
   * @param output the output stream to write to
   * @throws IOException if an I/O error occurs
   */
  public void writeHeadersOnly(final OutputStream output) throws IOException {
    final PrintWriter writer =
        new PrintWriter(new OutputStreamWriter(output, StandardCharsets.ISO_8859_1), false);

    // Status line
    writer.print(version.getValue());
    writer.print(" ");
    writer.print(status.getCode());
    writer.print(" ");
    writer.print(status.getReasonPhrase());
    writer.print("\r\n");

    // Header fields
    for (final String name : headers.names()) {
      writer.print(name);
      writer.print(": ");
      writer.print(headers.get(name));
      writer.print("\r\n");
    }

    // Empty line (no body follows)
    writer.print("\r\n");
    writer.flush();
  }

  @Override
  public String toString() {
    return String.format(
        "HttpResponse{status=%s, headers=%d, bodySize=%d}", status, headers.size(), body.length);
  }

  /**
   * Creates a standard HTML error response.
   *
   * <p>Generates an error response with HTML body containing the status code, reason phrase, and
   * optional custom message. Always sets Connection: close.
   *
   * @param status the HTTP error status
   * @param message optional custom message (can be null)
   * @return configured HttpResponse ready to send
   */
  public static HttpResponse errorResponse(final HttpStatus status, final String message) {
    final String errorBody =
        String.format(
            "<!DOCTYPE html>\n"
                + "<html>\n"
                + "<head><title>%d %s</title></head>\n"
                + "<body>\n"
                + "<h1>%d %s</h1>\n"
                + "%s"
                + "</body>\n"
                + "</html>\n",
            status.getCode(),
            status.getReasonPhrase(),
            status.getCode(),
            status.getReasonPhrase(),
            message != null ? "<p>" + escapeHtml(message) + "</p>\n" : "");

    return new HttpResponse()
        .status(status)
        .contentType("text/html; charset=UTF-8")
        .body(errorBody)
        .keepAlive(false);
  }

  /**
   * Creates a 404 Not Found error response.
   *
   * @return configured HttpResponse for 404 error
   */
  public static HttpResponse notFound() {
    return errorResponse(
        HttpStatus.NOT_FOUND, "The requested resource was not found on this server.");
  }

  /**
   * Creates a 405 Method Not Allowed error response.
   *
   * @param allowedMethods comma-separated list of allowed methods (e.g., "GET, HEAD")
   * @return configured HttpResponse for 405 error
   */
  public static HttpResponse methodNotAllowed(final String allowedMethods) {
    return errorResponse(HttpStatus.METHOD_NOT_ALLOWED, null).header("Allow", allowedMethods);
  }

  /**
   * Creates a 500 Internal Server Error response.
   *
   * @return configured HttpResponse for 500 error
   */
  public static HttpResponse internalServerError() {
    return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");
  }

  /**
   * Escapes HTML special characters to prevent XSS in error messages.
   *
   * @param text the text to escape
   * @return HTML-safe text
   */
  private static String escapeHtml(final String text) {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#x27;");
  }

  /** Functional interface mirroring Supplier but allowing checked IOExceptions. */
  @FunctionalInterface
  public interface InputStreamSupplier {
    InputStream get() throws IOException;
  }
}
