package ch.alejandrogarciahub.webserver.parser;

import ch.alejandrogarciahub.webserver.http.HttpHeaders;
import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import ch.alejandrogarciahub.webserver.http.HttpVersion;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Streaming HTTP/1.1 request parser with configurable limits.
 *
 * <p>This parser reads HTTP requests from an input stream and validates them against RFC 9112
 * requirements. All parsing limits are configurable via environment variables to prevent resource
 * exhaustion attacks.
 *
 * <p><strong>Configuration (Environment Variables):</strong>
 *
 * <ul>
 *   <li>{@code HTTP_MAX_REQUEST_LINE_LENGTH} - Maximum request line length in bytes (default: 8192)
 *   <li>{@code HTTP_MAX_HEADER_SIZE} - Maximum total header section size in bytes (default: 8192)
 *   <li>{@code HTTP_MAX_HEADERS_COUNT} - Maximum number of header fields (default: 100)
 *   <li>{@code HTTP_MAX_CONTENT_LENGTH} - Maximum request body size in bytes (default:
 *       10485760/10MB)
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> This parser is <em>not</em> thread-safe. Each connection
 * should use its own parser instance.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9112.html">RFC 9112 - HTTP/1.1</a>
 */
public final class HttpRequestParser {
  // Configurable limits
  private final int maxRequestLineLength;
  private final int maxHeaderSize;
  private final int maxHeadersCount;
  private final long maxContentLength;

  // HTTP protocol constants
  private static final byte CR = '\r';
  private static final byte LF = '\n';

  /**
   * Constructs an HttpRequestParser with specified limits.
   *
   * @param maxRequestLineLength maximum request line length in bytes
   * @param maxHeaderSize maximum total header section size in bytes
   * @param maxHeadersCount maximum number of header fields
   * @param maxContentLength maximum request body size in bytes
   */
  public HttpRequestParser(
      final int maxRequestLineLength,
      final int maxHeaderSize,
      final int maxHeadersCount,
      final long maxContentLength) {
    this.maxRequestLineLength = maxRequestLineLength;
    this.maxHeaderSize = maxHeaderSize;
    this.maxHeadersCount = maxHeadersCount;
    this.maxContentLength = maxContentLength;
  }

  /**
   * Parses an HTTP request from an input stream.
   *
   * <p>This method reads the request line, headers, and body (if present) from the stream. The
   * stream is read sequentially and validated against configured limits.
   *
   * @param input the input stream to read from (will be wrapped in BufferedInputStream if needed)
   * @return the parsed HttpRequest
   * @throws HttpParseException if parsing fails or limits are exceeded
   * @throws IOException if an I/O error occurs
   */
  public HttpRequest parse(final InputStream input) throws HttpParseException, IOException {
    final BufferedInputStream bufferedInput =
        input instanceof BufferedInputStream
            ? (BufferedInputStream) input
            : new BufferedInputStream(input);

    // Parse request line: Method SP Request-URI SP HTTP-Version CRLF
    // Graceful EOF handling: When the client closes a persistent connection cleanly (no more
    // requests pending), readLine() returns null to distinguish from parse errors. This allows
    // the connection loop to exit quietly without logging spurious errors.
    final String requestLine = readLine(bufferedInput, maxRequestLineLength);
    if (requestLine == null) {
      return null; // Graceful EOF before next request
    }
    if (requestLine.isEmpty()) {
      throw HttpParseException.badRequest("Empty request line");
    }

    final String[] requestLineParts = requestLine.split(" ");
    if (requestLineParts.length != 3) {
      throw HttpParseException.badRequest(
          "Invalid request line format: expected 'METHOD URI VERSION'");
    }

    final HttpMethod method;
    try {
      method = HttpMethod.parse(requestLineParts[0]);
    } catch (final IllegalArgumentException e) {
      throw new HttpParseException(
          "Unknown HTTP method: " + requestLineParts[0], HttpStatus.NOT_IMPLEMENTED, e);
    }

    final String requestTarget = requestLineParts[1];
    if (requestTarget.isEmpty()) {
      throw HttpParseException.badRequest("Empty request-target");
    }

    final HttpVersion version;
    try {
      version = HttpVersion.parse(requestLineParts[2]);
    } catch (final IllegalArgumentException e) {
      throw HttpParseException.httpVersionNotSupported(
          "Unsupported HTTP version: " + requestLineParts[2]);
    }

    // Parse headers: *(Header-Field CRLF)
    final HttpHeaders headers = parseHeaders(bufferedInput);

    // Validate Host header (required in HTTP/1.1)
    if (version == HttpVersion.HTTP_1_1 && !headers.contains("Host")) {
      throw HttpParseException.badRequest("Missing required Host header in HTTP/1.1");
    }

    // Parse body (if present)
    final byte[] body = parseBody(bufferedInput, headers);

    return new HttpRequest(method, requestTarget, version, headers, body);
  }

  /**
   * Parses HTTP headers from the input stream.
   *
   * <p>Reads headers until an empty line (CRLF) is encountered. Validates total header size and
   * count against configured limits.
   *
   * @param input the buffered input stream
   * @return the parsed headers
   * @throws HttpParseException if header parsing fails or limits are exceeded
   * @throws IOException if an I/O error occurs
   */
  private HttpHeaders parseHeaders(final BufferedInputStream input)
      throws HttpParseException, IOException {
    final HttpHeaders headers = new HttpHeaders();
    int totalHeaderSize = 0;
    int headerCount = 0;

    while (true) {
      // Graceful EOF handling: If client closes mid-request (after request line but before
      // completing headers), readLine() returns null. This is a parse error, not graceful close.
      final String line = readLine(input, maxHeaderSize - totalHeaderSize);
      if (line == null) {
        throw HttpParseException.badRequest("Unexpected end of stream while reading headers");
      }
      totalHeaderSize += line.length() + 2; // +2 for CRLF

      // Empty line signals end of headers
      if (line.isEmpty()) {
        break;
      }

      // Check header count limit
      if (++headerCount > maxHeadersCount) {
        throw HttpParseException.badRequest(
            "Too many headers: exceeds limit of " + maxHeadersCount);
      }

      // Parse header field: Name: Value
      final int colonIndex = line.indexOf(':');
      if (colonIndex <= 0) {
        throw HttpParseException.badRequest("Invalid header format: missing colon");
      }

      final String name = line.substring(0, colonIndex).trim();
      final String value = line.substring(colonIndex + 1).trim();

      if (name.isEmpty()) {
        throw HttpParseException.badRequest("Empty header field name");
      }

      // Validate header field name (RFC 9110: token characters only)
      if (!isValidHeaderName(name)) {
        throw HttpParseException.badRequest("Invalid header field name: " + name);
      }

      headers.set(name, value);
    }

    // Check total header size limit
    if (totalHeaderSize > maxHeaderSize) {
      throw HttpParseException.badRequest(
          "Headers too large: exceeds limit of " + maxHeaderSize + " bytes");
    }

    return headers;
  }

  /**
   * Parses the request body based on Content-Length or Transfer-Encoding headers.
   *
   * <p>Supports two body encoding methods:
   *
   * <ul>
   *   <li><strong>Content-Length:</strong> Read exact number of bytes specified
   *   <li><strong>Transfer-Encoding: chunked:</strong> Read chunked body (currently supported)
   * </ul>
   *
   * @param input the buffered input stream
   * @param headers the request headers
   * @return the request body bytes (empty array if no body)
   * @throws HttpParseException if body parsing fails or exceeds size limit
   * @throws IOException if an I/O error occurs
   */
  private byte[] parseBody(final BufferedInputStream input, final HttpHeaders headers)
      throws HttpParseException, IOException {
    // Check for Transfer-Encoding: chunked
    final String transferEncoding = headers.get("Transfer-Encoding");
    if ("chunked".equalsIgnoreCase(transferEncoding)) {
      return parseChunkedBody(input);
    }

    // Check for Content-Length
    final String contentLengthStr = headers.get("Content-Length");
    if (contentLengthStr == null) {
      return new byte[0]; // No body
    }

    final long contentLength;
    try {
      contentLength = Long.parseLong(contentLengthStr);
    } catch (final NumberFormatException e) {
      throw HttpParseException.badRequest("Invalid Content-Length header: " + contentLengthStr);
    }

    if (contentLength < 0) {
      throw HttpParseException.badRequest("Negative Content-Length: " + contentLength);
    }

    if (contentLength > maxContentLength) {
      throw HttpParseException.payloadTooLarge(
          "Content-Length " + contentLength + " exceeds limit of " + maxContentLength);
    }

    // Read exact number of bytes
    final byte[] body = new byte[(int) contentLength];
    int totalRead = 0;
    while (totalRead < contentLength) {
      final int bytesRead = input.read(body, totalRead, (int) (contentLength - totalRead));
      if (bytesRead == -1) {
        throw HttpParseException.badRequest(
            "Unexpected end of stream: expected " + contentLength + " bytes, got " + totalRead);
      }
      totalRead += bytesRead;
    }

    return body;
  }

  /**
   * Parses a chunked request body per RFC 9112 Section 7.1.
   *
   * <p>Chunked format: chunk-size CRLF chunk-data CRLF ... 0 CRLF CRLF
   *
   * @param input the buffered input stream
   * @return the decoded body bytes
   * @throws HttpParseException if chunked parsing fails
   * @throws IOException if an I/O error occurs
   */
  private byte[] parseChunkedBody(final BufferedInputStream input)
      throws HttpParseException, IOException {
    final ByteArrayOutputStream bodyOutput = new ByteArrayOutputStream();
    long totalSize = 0;

    while (true) {
      // Read chunk size line
      final String chunkSizeLine = readLine(input, 1024);
      if (chunkSizeLine == null) {
        throw HttpParseException.badRequest("Unexpected end of stream before chunk size");
      }
      if (chunkSizeLine.isEmpty()) {
        throw HttpParseException.badRequest("Empty chunk size line");
      }

      // Parse chunk size (hex, may include chunk extensions separated by semicolon)
      final int semicolonIndex = chunkSizeLine.indexOf(';');
      final String chunkSizeStr =
          semicolonIndex > 0 ? chunkSizeLine.substring(0, semicolonIndex) : chunkSizeLine;

      final int chunkSize;
      try {
        chunkSize = Integer.parseInt(chunkSizeStr.trim(), 16);
      } catch (final NumberFormatException e) {
        throw HttpParseException.badRequest("Invalid chunk size: " + chunkSizeStr);
      }

      if (chunkSize < 0) {
        throw HttpParseException.badRequest("Negative chunk size: " + chunkSize);
      }

      // Last chunk (size 0) signals end of body
      if (chunkSize == 0) {
        // Read trailing headers (if any) until empty line
        while (true) {
          final String trailerLine = readLine(input, maxHeaderSize);
          if (trailerLine == null) {
            throw HttpParseException.badRequest("Unexpected end of stream in chunk trailers");
          }
          if (trailerLine.isEmpty()) {
            break; // End of trailers
          }
          // Trailer headers are currently ignored
        }
        break;
      }

      // Check total size limit
      totalSize += chunkSize;
      if (totalSize > maxContentLength) {
        throw HttpParseException.payloadTooLarge(
            "Chunked body size " + totalSize + " exceeds limit of " + maxContentLength);
      }

      // Read chunk data
      final byte[] chunkData = new byte[chunkSize];
      int totalRead = 0;
      while (totalRead < chunkSize) {
        final int bytesRead = input.read(chunkData, totalRead, chunkSize - totalRead);
        if (bytesRead == -1) {
          throw HttpParseException.badRequest(
              "Unexpected end of stream reading chunk data: expected "
                  + chunkSize
                  + " bytes, got "
                  + totalRead);
        }
        totalRead += bytesRead;
      }
      bodyOutput.write(chunkData);

      // Read trailing CRLF after chunk data
      final String trailingCrlf = readLine(input, 2);
      if (trailingCrlf == null) {
        throw HttpParseException.badRequest("Unexpected end of stream after chunk data");
      }
      if (!trailingCrlf.isEmpty()) {
        throw HttpParseException.badRequest("Missing CRLF after chunk data");
      }
    }

    return bodyOutput.toByteArray();
  }

  /**
   * Reads a single line from the input stream until CRLF.
   *
   * <p>Returns the line without the trailing CRLF. Validates line length against provided limit.
   *
   * <p>Improved CR/LF parsing: This implementation uses a two-state approach where CR sets a flag
   * but is NOT added to the buffer. When LF follows, we return the line immediately without needing
   * to remove CR. This is cleaner than the alternative of adding CR to buffer and removing it
   * later.
   *
   * <p>Graceful EOF: Returns null when EOF occurs before reading any data, allowing the connection
   * loop to distinguish clean connection close (HTTP pipelining end) from parse errors mid-request.
   *
   * @param input the buffered input stream
   * @param maxLength the maximum line length allowed
   * @return the line (without CRLF), or null on graceful EOF, or empty string for empty line
   * @throws HttpParseException if line exceeds maxLength or invalid line ending
   * @throws IOException if an I/O error occurs
   */
  private String readLine(final BufferedInputStream input, final int maxLength)
      throws HttpParseException, IOException {
    final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();
    boolean prevWasCR = false;
    boolean readAny = false;

    while (true) {
      final int b = input.read();
      if (b == -1) {
        if (!readAny) {
          return null; // Graceful EOF before any data
        }
        throw HttpParseException.badRequest("Unexpected end of stream while reading line");
      }

      readAny = true;

      if (prevWasCR) {
        if (b == LF) {
          return new String(lineBuffer.toByteArray(), StandardCharsets.ISO_8859_1);
        }
        throw HttpParseException.badRequest("Malformed line ending: expected LF after CR");
      }

      // CR is NOT added to buffer - cleaner two-state approach
      if (b == CR) {
        prevWasCR = true;
        continue;
      }

      if (lineBuffer.size() >= maxLength) {
        throw HttpParseException.uriTooLong(
            "Line exceeds maximum length of " + maxLength + " bytes (excluding CRLF)");
      }

      lineBuffer.write(b);
    }
  }

  /**
   * Validates header field name per RFC 9110.
   *
   * <p>Header field names must be tokens (RFC 9110 Section 5.1): tchar = "!" / "#" / "$" / "%" /
   * "&" / "'" / "*" / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~" / DIGIT / ALPHA
   *
   * @param name the header field name
   * @return true if valid token
   */
  private boolean isValidHeaderName(final String name) {
    if (name.isEmpty()) {
      return false;
    }

    for (int i = 0; i < name.length(); i++) {
      final char c = name.charAt(i);
      if (!isTokenChar(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks if character is a valid token character per RFC 9110.
   *
   * @param c the character
   * @return true if valid token character
   */
  private boolean isTokenChar(final char c) {
    // ALPHA / DIGIT
    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
      return true;
    }
    // Special characters
    return c == '!' || c == '#' || c == '$' || c == '%' || c == '&' || c == '\'' || c == '*'
        || c == '+' || c == '-' || c == '.' || c == '^' || c == '_' || c == '`' || c == '|'
        || c == '~';
  }
}
