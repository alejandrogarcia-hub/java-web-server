package ch.alejandrogarciahub.webserver.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HttpResponse} focusing on builder pattern, lazy streaming, and connection
 * directives.
 *
 * <p>Critical areas: Connection header correctness (HTTP/1.1 vs 1.0), lazy streaming (memory
 * efficiency), handler directive priority (connection management).
 */
class HttpResponseTest {

  // Connection Directive Tests - Critical for Keep-Alive

  @Test
  void shouldNotSetConnectionHeaderHttp11WhenKeepAlive() {
    // Critical: HTTP/1.1 persistent by default, NO header needed (RFC 9112)
    final HttpResponse response = new HttpResponse().version(HttpVersion.HTTP_1_1).keepAlive(true);

    final HttpHeaders headers = getHeaders(response);
    assertThat(headers.contains("Connection")).isFalse();
  }

  @Test
  void shouldSetConnectionCloseHttp11WhenClosing() {
    // Critical: Only send Connection: close when actually closing
    final HttpResponse response = new HttpResponse().version(HttpVersion.HTTP_1_1).keepAlive(false);

    final HttpHeaders headers = getHeaders(response);
    assertThat(headers.get("Connection")).isEqualToIgnoringCase("close");
  }

  @Test
  void shouldSetConnectionKeepAliveHttp10WhenKeepAlive() {
    // Critical: HTTP/1.0 needs explicit keep-alive header
    final HttpResponse response = new HttpResponse().version(HttpVersion.HTTP_1_0).keepAlive(true);

    final HttpHeaders headers = getHeaders(response);
    assertThat(headers.get("Connection")).isEqualToIgnoringCase("keep-alive");
  }

  @Test
  void shouldSetConnectionCloseHttp10WhenClosing() {
    final HttpResponse response = new HttpResponse().version(HttpVersion.HTTP_1_0).keepAlive(false);

    final HttpHeaders headers = getHeaders(response);
    assertThat(headers.get("Connection")).isEqualToIgnoringCase("close");
  }

  @Test
  void shouldTrackExplicitConnectionDirective() {
    // Handler can explicitly set Connection header
    final HttpResponse response = new HttpResponse().header("Connection", "close");

    assertThat(response.hasConnectionDirective()).isTrue();
    assertThat(response.isConnectionPersistent()).isFalse();
  }

  @Test
  void shouldNotTrackDirectiveWhenUsingKeepAlive() {
    // keepAlive() sets header but marks it as non-explicit for HTTP/1.1
    final HttpResponse response = new HttpResponse().version(HttpVersion.HTTP_1_1).keepAlive(true);

    // HTTP/1.1 keep-alive removes header, so no directive
    assertThat(response.hasConnectionDirective()).isFalse();
  }

  @Test
  void shouldFallbackToVersionDefaultsNoExplicitDirective() {
    final HttpResponse http11 = new HttpResponse().version(HttpVersion.HTTP_1_1);
    final HttpResponse http10 = new HttpResponse().version(HttpVersion.HTTP_1_0);

    // No explicit directive, use version defaults
    assertThat(http11.isConnectionPersistent()).isTrue(); // HTTP/1.1 default
    assertThat(http10.isConnectionPersistent()).isFalse(); // HTTP/1.0 default
  }

  // Builder Pattern Tests

  @Test
  void shouldSupportMethodChaining() {
    final HttpResponse response =
        new HttpResponse()
            .status(HttpStatus.OK)
            .version(HttpVersion.HTTP_1_1)
            .header("Content-Type", "text/html")
            .body("test");

    assertThat(response).isNotNull();
  }

  @Test
  void shouldSetDefaultStatus200() {
    final HttpResponse response = new HttpResponse();

    // Verify by writing and checking output
    final String output = writeToString(response);
    assertThat(output).startsWith("HTTP/1.1 200 OK");
  }

  @Test
  void shouldSetDefaultVersionHttp11() {
    final HttpResponse response = new HttpResponse();

    final String output = writeToString(response);
    assertThat(output).startsWith("HTTP/1.1");
  }

  @Test
  void shouldSetServerHeaderByDefault() {
    final HttpResponse response = new HttpResponse();

    final HttpHeaders headers = getHeaders(response);
    assertThat(headers.get("Server")).contains("Java-WebServer");
  }

  // Body Handling Tests

  @Test
  void shouldSetBodyFromByteArray() {
    final byte[] body = "test body".getBytes();
    final HttpResponse response = new HttpResponse().body(body);

    final HttpHeaders headers = getHeaders(response);
    assertThat(headers.get("Content-Length")).isEqualTo("9");
  }

  @Test
  void shouldSetBodyFromString() {
    final HttpResponse response = new HttpResponse().body("test body");

    final HttpHeaders headers = getHeaders(response);
    assertThat(headers.get("Content-Length")).isEqualTo("9");

    final String output = writeToString(response);
    assertThat(output).endsWith("test body");
  }

  @Test
  void shouldSetContentLengthAutomatically() {
    final HttpResponse response = new HttpResponse().body("hello world");

    final HttpHeaders headers = getHeaders(response);
    assertThat(headers.get("Content-Length")).isEqualTo("11");
  }

  @Test
  void shouldCreateDefensiveCopyOfBody() {
    // Critical: Prevent external modification
    final byte[] original = "test".getBytes();
    final HttpResponse response = new HttpResponse().body(original);

    original[0] = 'X'; // Modify original

    final String output = writeToString(response);
    assertThat(output).endsWith("test"); // Body unchanged
  }

  // Lazy Streaming Tests

  @Test
  void shouldDeclareBodyLengthWithoutMaterializing() {
    final HttpResponse response = new HttpResponse().bodyLength(1000);

    final HttpHeaders headers = getHeaders(response);
    assertThat(headers.get("Content-Length")).isEqualTo("1000");

    // Body should be empty until supplier is invoked
    final String output = writeToString(response);
    assertThat(output).doesNotContain("1000 bytes of data");
  }

  @Test
  void shouldStreamBodyFromSupplier() throws IOException {
    final String content = "streamed content";
    final HttpResponse response =
        new HttpResponse()
            .bodyLength(content.length())
            .setBodySupplier(() -> new ByteArrayInputStream(content.getBytes()));

    final String output = writeToString(response);
    assertThat(output).endsWith("streamed content");
  }

  @Test
  void shouldPropagateSupplierIoException() {
    final HttpResponse response =
        new HttpResponse()
            .bodyLength(10)
            .setBodySupplier(
                () -> {
                  throw new IOException("boom");
                });

    assertThatThrownBy(() -> response.writeTo(new ByteArrayOutputStream()))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("boom");
  }

  @Test
  void shouldClearBodySupplierWhenSettingConcreteBody() {
    final HttpResponse response =
        new HttpResponse()
            .bodyLength(100)
            .setBodySupplier(() -> new ByteArrayInputStream("supplier".getBytes()))
            .body("concrete"); // Should clear supplier

    final String output = writeToString(response);
    assertThat(output).endsWith("concrete");
    assertThat(output).doesNotContain("supplier");
  }

  // Error Response Factory Tests

  @Test
  void shouldCreateNotFoundResponse() {
    final HttpResponse response = HttpResponse.notFound();

    assertThat(response).isNotNull();
    final String output = writeToString(response);
    assertThat(output).contains("404 Not Found");
    assertThat(output).contains("text/html");
    assertThat(output).contains("Connection: close");
  }

  @Test
  void shouldCreateMethodNotAllowedResponse() {
    final HttpResponse response = HttpResponse.methodNotAllowed("GET, HEAD");

    final String output = writeToString(response);
    assertThat(output).contains("405 Method Not Allowed");
    assertThat(output).contains("Allow: GET, HEAD");
    assertThat(output).contains("Connection: close");
  }

  @Test
  void shouldCreateInternalServerErrorResponse() {
    final HttpResponse response = HttpResponse.internalServerError();

    final String output = writeToString(response);
    assertThat(output).contains("500 Internal Server Error");
    assertThat(output).contains("Connection: close");
  }

  @Test
  void shouldEscapeHtmlInErrorMessages() {
    // Security: Prevent XSS in error messages
    final HttpResponse response =
        HttpResponse.errorResponse(HttpStatus.BAD_REQUEST, "<script>alert('xss')</script>");

    final String output = writeToString(response);
    assertThat(output).contains("&lt;script&gt;");
    assertThat(output).contains("&lt;/script&gt;");
    assertThat(output).doesNotContain("<script>");
  }

  // Write Operations Tests

  @Test
  void shouldWriteCompleteResponse() {
    final HttpResponse response =
        new HttpResponse()
            .status(HttpStatus.OK)
            .header("Content-Type", "text/plain")
            .body("Hello World");

    final String output = writeToString(response);

    // Verify structure
    assertThat(output).startsWith("HTTP/1.1 200 OK\r\n");
    assertThat(output).contains("Content-Type: text/plain\r\n");
    assertThat(output).contains("Content-Length: 11\r\n");
    assertThat(output).contains("\r\n\r\n"); // Empty line before body
    assertThat(output).endsWith("Hello World");
  }

  @Test
  void shouldWriteHeadersOnlyForHead() throws IOException {
    final HttpResponse response =
        new HttpResponse().header("Content-Type", "text/html").body("body content");

    final ByteArrayOutputStream output = new ByteArrayOutputStream();
    response.writeHeadersOnly(output);

    final String result = output.toString();
    assertThat(result).contains("Content-Type: text/html");
    assertThat(result).contains("Content-Length: 12"); // Body length set
    assertThat(result).doesNotContain("body content"); // No body
  }

  @Test
  void shouldUseCrlfLineEndings() {
    final HttpResponse response = new HttpResponse().header("Test", "value").body("body");

    final String output = writeToString(response);

    // RFC requires CRLF (\r\n) not just LF (\n)
    assertThat(output).contains("\r\n");
    assertThat(output.split("\r\n")).hasSizeGreaterThan(3);
  }

  // Helper Methods

  private HttpHeaders getHeaders(final HttpResponse response) {
    // Use reflection to access private headers field
    try {
      final var field = HttpResponse.class.getDeclaredField("headers");
      field.setAccessible(true);
      return (HttpHeaders) field.get(response);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to get headers", e);
    }
  }

  private String writeToString(final HttpResponse response) {
    try {
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      response.writeTo(output);
      return output.toString();
    } catch (final IOException e) {
      throw new RuntimeException("Failed to write response", e);
    }
  }
}
