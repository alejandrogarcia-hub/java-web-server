package ch.alejandrogarciahub.webserver.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import ch.alejandrogarciahub.webserver.http.HttpVersion;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HttpRequestParser} focusing on RFC 9112 compliance and security.
 *
 * <p>Critical areas: DoS prevention (limits), graceful EOF (pipelining), malformed input
 * (security), RFC compliance.
 */
class HttpRequestParserTest {

  private static final HttpRequestParser DEFAULT_PARSER =
      new HttpRequestParser(8192, 8192, 100, 10 * 1024 * 1024);

  // Request Line Parsing - Happy Path

  @Test
  void shouldParseValidGetRequest() throws Exception {
    final String request = "GET / HTTP/1.1\r\nHost: example.com\r\n\r\n";
    final HttpRequest result = parse(request);

    assertThat(result.getMethod()).isEqualTo(HttpMethod.GET);
    assertThat(result.getPath()).isEqualTo("/");
    assertThat(result.getVersion()).isEqualTo(HttpVersion.HTTP_1_1);
  }

  @Test
  void shouldParseRequestWithPath() throws Exception {
    final String request = "GET /index.html HTTP/1.1\r\nHost: example.com\r\n\r\n";
    final HttpRequest result = parse(request);

    assertThat(result.getPath()).isEqualTo("/index.html");
  }

  @Test
  void shouldParseHttp10Request() throws Exception {
    final String request = "GET / HTTP/1.0\r\n\r\n";
    final HttpRequest result = parse(request);

    assertThat(result.getVersion()).isEqualTo(HttpVersion.HTTP_1_0);
  }

  @Test
  void shouldParseAllHttpMethods() throws Exception {
    for (HttpMethod method : HttpMethod.values()) {
      final String request = method.name() + " / HTTP/1.1\r\nHost: example.com\r\n\r\n";
      final HttpRequest result = parse(request);

      assertThat(result.getMethod()).isEqualTo(method);
    }
  }

  // Request Line Parsing - Error Cases

  @Test
  void shouldRejectEmptyRequestLine() {
    final String request = "\r\nHost: example.com\r\n\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Empty request line");
  }

  @Test
  void shouldRejectMalformedRequestLine() {
    // Missing HTTP version
    final String request = "GET /\r\nHost: example.com\r\n\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Invalid request line format");
  }

  @Test
  void shouldRejectUnknownHttpMethod() {
    final String request = "INVALID / HTTP/1.1\r\nHost: example.com\r\n\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .extracting(e -> ((HttpParseException) e).getStatus())
        .isEqualTo(HttpStatus.NOT_IMPLEMENTED);
  }

  @Test
  void shouldRejectUnsupportedHttpVersion() {
    final String request = "GET / HTTP/2.0\r\nHost: example.com\r\n\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .extracting(e -> ((HttpParseException) e).getStatus())
        .isEqualTo(HttpStatus.HTTP_VERSION_NOT_SUPPORTED);
  }

  @Test
  void shouldRejectRequestLineExceedingMaxLength() {
    // Create request line exceeding limit
    final String longPath = "/" + "a".repeat(9000);
    final HttpRequestParser parser = new HttpRequestParser(100, 8192, 100, 10_485_760);
    final String request = "GET " + longPath + " HTTP/1.1\r\nHost: example.com\r\n\r\n";

    assertThatThrownBy(() -> parser.parse(toInputStream(request)))
        .isInstanceOf(HttpParseException.class)
        .extracting(e -> ((HttpParseException) e).getStatus())
        .isEqualTo(HttpStatus.URI_TOO_LONG);
  }

  // Header Parsing - Happy Path

  @Test
  void shouldParseSimpleHeaders() throws Exception {
    final String request =
        "GET / HTTP/1.1\r\n" + "Host: example.com\r\n" + "Content-Type: text/html\r\n" + "\r\n";

    final HttpRequest result = parse(request);

    assertThat(result.getHeaders().get("Host")).isEqualTo("example.com");
    assertThat(result.getHeaders().get("Content-Type")).isEqualTo("text/html");
  }

  @Test
  void shouldTrimWhitespaceInHeaderValues() throws Exception {
    final String request = "GET / HTTP/1.1\r\n" + "Host:   example.com   \r\n" + "\r\n";

    final HttpRequest result = parse(request);

    assertThat(result.getHeaders().get("Host")).isEqualTo("example.com");
  }

  @Test
  void shouldParseEmptyHeaderValue() throws Exception {
    final String request =
        "GET / HTTP/1.1\r\n" + "Host: example.com\r\n" + "X-Custom:\r\n" + "\r\n";

    final HttpRequest result = parse(request);

    assertThat(result.getHeaders().get("X-Custom")).isEqualTo("");
  }

  // Header Parsing - RFC Compliance

  @Test
  void shouldRequireHostHeaderForHttp11() {
    // RFC 9112: HTTP/1.1 requires Host header
    final String request = "GET / HTTP/1.1\r\n\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Missing required Host header");
  }

  @Test
  void shouldNotRequireHostHeaderForHttp10() throws Exception {
    // HTTP/1.0 doesn't require Host header
    final String request = "GET / HTTP/1.0\r\n\r\n";

    final HttpRequest result = parse(request);

    assertThat(result.getVersion()).isEqualTo(HttpVersion.HTTP_1_0);
  }

  // Header Parsing - Security & Limits

  @Test
  void shouldRejectInvalidHeaderName() {
    // Header name with invalid characters
    final String request = "GET / HTTP/1.1\r\n" + "Invalid Header: value\r\n" + "\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Invalid header field name");
  }

  @Test
  void shouldRejectHeaderWithoutColon() {
    final String request = "GET / HTTP/1.1\r\n" + "InvalidHeader\r\n" + "\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Invalid header format");
  }

  @Test
  void shouldRejectTooManyHeaders() {
    final HttpRequestParser parser = new HttpRequestParser(8192, 8192, 5, 10_485_760);
    final StringBuilder request = new StringBuilder("GET / HTTP/1.1\r\n");
    for (int i = 0; i < 10; i++) {
      request.append("Header").append(i).append(": value\r\n");
    }
    request.append("\r\n");

    assertThatThrownBy(() -> parser.parse(toInputStream(request.toString())))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Too many headers");
  }

  @Test
  void shouldRejectHeadersSectionExceedingSize() {
    final HttpRequestParser parser = new HttpRequestParser(8192, 100, 100, 10_485_760);
    final String largeValue = "x".repeat(200);
    final String request =
        "GET / HTTP/1.1\r\n" + "Host: example.com\r\n" + "Large: " + largeValue + "\r\n" + "\r\n";

    assertThatThrownBy(() -> parser.parse(toInputStream(request)))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("exceeds maximum length");
  }

  // Body Parsing - Content-Length

  @Test
  void shouldParseBodyWithContentLength() throws Exception {
    final String request =
        "POST /api HTTP/1.1\r\n"
            + "Host: example.com\r\n"
            + "Content-Length: 11\r\n"
            + "\r\n"
            + "hello world";

    final HttpRequest result = parse(request);

    assertThat(new String(result.getBody())).isEqualTo("hello world");
  }

  @Test
  void shouldHandleZeroContentLength() throws Exception {
    final String request =
        "POST /api HTTP/1.1\r\n" + "Host: example.com\r\n" + "Content-Length: 0\r\n" + "\r\n";

    final HttpRequest result = parse(request);

    assertThat(result.getBody()).isEmpty();
  }

  @Test
  void shouldRejectNegativeContentLength() {
    final String request =
        "POST /api HTTP/1.1\r\n" + "Host: example.com\r\n" + "Content-Length: -1\r\n" + "\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Negative Content-Length");
  }

  @Test
  void shouldRejectInvalidContentLength() {
    final String request =
        "POST /api HTTP/1.1\r\n" + "Host: example.com\r\n" + "Content-Length: abc\r\n" + "\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Invalid Content-Length");
  }

  @Test
  void shouldRejectBodyExceedingMaxContentLength() {
    final HttpRequestParser parser = new HttpRequestParser(8192, 8192, 100, 10);
    final String request =
        "POST /api HTTP/1.1\r\n"
            + "Host: example.com\r\n"
            + "Content-Length: 20\r\n"
            + "\r\n"
            + "x".repeat(20);

    assertThatThrownBy(() -> parser.parse(toInputStream(request)))
        .isInstanceOf(HttpParseException.class)
        .extracting(e -> ((HttpParseException) e).getStatus())
        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
  }

  // Body Parsing - Chunked Transfer Encoding

  @Test
  void shouldParseChunkedBodySingleChunk() throws Exception {
    final String request =
        "POST /upload HTTP/1.1\r\n"
            + "Host: example.com\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "\r\n"
            + "5\r\n"
            + "hello\r\n"
            + "0\r\n"
            + "\r\n";

    final HttpRequest result = parse(request);

    assertThat(new String(result.getBody())).isEqualTo("hello");
  }

  @Test
  void shouldParseChunkedBodyMultipleChunks() throws Exception {
    final String request =
        "POST /upload HTTP/1.1\r\n"
            + "Host: example.com\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "\r\n"
            + "5\r\n"
            + "hello\r\n"
            + "6\r\n"
            + " world\r\n"
            + "0\r\n"
            + "\r\n";

    final HttpRequest result = parse(request);

    assertThat(new String(result.getBody())).isEqualTo("hello world");
  }

  @Test
  void shouldParseChunkedBodyWithExtensions() throws Exception {
    // Chunk extensions should be ignored
    final String request =
        "POST /upload HTTP/1.1\r\n"
            + "Host: example.com\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "\r\n"
            + "5;ext=value\r\n"
            + "hello\r\n"
            + "0\r\n"
            + "\r\n";

    final HttpRequest result = parse(request);

    assertThat(new String(result.getBody())).isEqualTo("hello");
  }

  @Test
  void shouldRejectInvalidChunkSize() {
    final String request =
        "POST /upload HTTP/1.1\r\n"
            + "Host: example.com\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "\r\n"
            + "XYZ\r\n"
            + "hello\r\n"
            + "0\r\n"
            + "\r\n";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Invalid chunk size");
  }

  @Test
  void shouldRejectChunkedBodyExceedingMaxLength() {
    final HttpRequestParser parser = new HttpRequestParser(8192, 8192, 100, 10);
    final String request =
        "POST /upload HTTP/1.1\r\n"
            + "Host: example.com\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "\r\n"
            + "14\r\n" // 20 bytes
            + "x".repeat(20)
            + "\r\n"
            + "0\r\n"
            + "\r\n";

    assertThatThrownBy(() -> parser.parse(toInputStream(request)))
        .isInstanceOf(HttpParseException.class)
        .extracting(e -> ((HttpParseException) e).getStatus())
        .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
  }

  // Graceful EOF - Critical for HTTP Pipelining

  @Test
  void shouldReturnNullOnGracefulEof() throws Exception {
    // Empty input = clean connection close
    final HttpRequest result = DEFAULT_PARSER.parse(toInputStream(""));

    assertThat(result).isNull();
  }

  @Test
  void shouldRejectEofDuringHeaders() {
    // EOF after partial request line
    final String request = "GET / HTTP/1.1\r\nHost: exam";

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Unexpected end of stream");
  }

  @Test
  void shouldRejectEofDuringBody() {
    final String request =
        "POST /api HTTP/1.1\r\n"
            + "Host: example.com\r\n"
            + "Content-Length: 10\r\n"
            + "\r\n"
            + "short"; // Only 5 bytes, expected 10

    assertThatThrownBy(() -> parse(request))
        .isInstanceOf(HttpParseException.class)
        .hasMessageContaining("Unexpected end of stream");
  }

  // Helper Methods

  private HttpRequest parse(final String request) throws IOException, HttpParseException {
    return DEFAULT_PARSER.parse(toInputStream(request));
  }

  private ByteArrayInputStream toInputStream(final String str) {
    return new ByteArrayInputStream(str.getBytes(StandardCharsets.ISO_8859_1));
  }
}
