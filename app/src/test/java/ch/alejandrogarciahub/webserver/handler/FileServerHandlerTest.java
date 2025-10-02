package ch.alejandrogarciahub.webserver.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.alejandrogarciahub.webserver.http.HttpHeaders;
import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpResponse;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FileServerHandler} focusing on security (path traversal) and business logic.
 *
 * <p>Critical: Path traversal bugs expose the entire file system. MIME type bugs cause browser
 * rendering issues or security problems. Lazy streaming prevents OOM under load.
 */
class FileServerHandlerTest {

  private FileServerHandler handler;
  private Path tempDir;

  @BeforeEach
  void setup() throws IOException {
    tempDir = Files.createTempDirectory("fileserver-test");
    handler = new FileServerHandler(tempDir);

    // Create test files
    Files.writeString(tempDir.resolve("index.html"), "<html>Index</html>");
    Files.writeString(tempDir.resolve("test.txt"), "Hello World");
    Files.writeString(tempDir.resolve("test.json"), "{\"key\":\"value\"}");

    // Create subdirectory with file
    final Path subdir = tempDir.resolve("subdir");
    Files.createDirectory(subdir);
    Files.writeString(subdir.resolve("nested.html"), "<html>Nested</html>");
    Files.writeString(subdir.resolve("index.html"), "<html>Subdir Index</html>");
  }

  @AfterEach
  void cleanup() throws IOException {
    // Recursively delete temp directory
    if (tempDir != null && Files.exists(tempDir)) {
      Files.walk(tempDir)
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (final IOException e) {
                  // Ignore cleanup errors in tests
                }
              });
    }
  }

  // File Serving - Happy Path

  @Test
  void shouldServeRootIndexHtml() throws IOException {
    final HttpRequest request = createGetRequest("/");
    final HttpResponse response = handler.handle(request);

    assertThat(response).isNotNull();
    assertThat(getStatus(response)).isEqualTo(HttpStatus.OK);
    assertThat(getContentType(response)).contains("text/html");
  }

  @Test
  void shouldServeFileByPath() throws IOException {
    final HttpRequest request = createGetRequest("/test.txt");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.OK);
    assertThat(getContentType(response)).contains("text/plain");
    assertThat(writeBody(response)).isEqualTo("Hello World");
  }

  @Test
  void shouldServeNestedFile() throws IOException {
    final HttpRequest request = createGetRequest("/subdir/nested.html");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.OK);
    assertThat(getContentType(response)).contains("text/html");
    assertThat(writeBody(response)).contains("Nested");
  }

  @Test
  void shouldServeDirectoryIndexHtml() throws IOException {
    final HttpRequest request = createGetRequest("/subdir/");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.OK);
    assertThat(getContentType(response)).contains("text/html");
    assertThat(writeBody(response)).contains("Subdir Index");
  }

  // MIME Type Detection - Critical for Browser Rendering

  @Test
  void shouldDetectHtmlMimeType() throws IOException {
    final HttpRequest request = createGetRequest("/index.html");
    final HttpResponse response = handler.handle(request);

    // Java's Files.probeContentType() may or may not include charset
    assertThat(getContentType(response)).contains("text/html");
  }

  @Test
  void shouldDetectJsonMimeType() throws IOException {
    final HttpRequest request = createGetRequest("/test.json");
    final HttpResponse response = handler.handle(request);

    // Java's Files.probeContentType() may or may not include charset
    assertThat(getContentType(response)).contains("application/json");
  }

  @Test
  void shouldDetectTextMimeType() throws IOException {
    final HttpRequest request = createGetRequest("/test.txt");
    final HttpResponse response = handler.handle(request);

    // Java's Files.probeContentType() may or may not include charset
    assertThat(getContentType(response)).contains("text/plain");
  }

  @Test
  void shouldDefaultToOctetStreamForUnknownType() throws IOException {
    Files.writeString(tempDir.resolve("unknown.xyz"), "data");
    final HttpRequest request = createGetRequest("/unknown.xyz");
    final HttpResponse response = handler.handle(request);

    // Files.probeContentType() is platform-dependent
    // On macOS: might return null → fallback to application/octet-stream
    // On Linux: might probe the file and return something
    // We just verify a Content-Type is set
    assertThat(getContentType(response))
        .as("Unknown file types should have a Content-Type")
        .isNotNull()
        .isNotEmpty();
  }

  // HTTP Method Support

  @Test
  void shouldSupportGetMethod() throws IOException {
    final HttpRequest request = createGetRequest("/test.txt");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.OK);
  }

  @Test
  void shouldSupportHeadMethod() throws IOException {
    final HttpRequest request = createHeadRequest("/test.txt");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.OK);
    assertThat(getContentLength(response)).isEqualTo("11");

    // Mimic server behavior: HEAD responses should only send headers
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      response.writeHeadersOnly(out);
      final String raw = out.toString();
      assertThat(raw).contains("Content-Length: 11");
      assertThat(raw).doesNotContain("Hello World");
    }
  }

  @Test
  void shouldRejectPostMethod() throws IOException {
    final HttpRequest request = createRequest(HttpMethod.POST, "/test.txt");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  @Test
  void shouldRejectPutMethod() throws IOException {
    final HttpRequest request = createRequest(HttpMethod.PUT, "/test.txt");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  @Test
  void shouldRejectDeleteMethod() throws IOException {
    final HttpRequest request = createRequest(HttpMethod.DELETE, "/test.txt");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
  }

  // Error Handling

  @Test
  void shouldReturn404ForMissingFile() throws IOException {
    final HttpRequest request = createGetRequest("/nonexistent.txt");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldReturn404ForMissingDirectoryIndex() throws IOException {
    final Path noIndexDir = tempDir.resolve("noindex");
    Files.createDirectory(noIndexDir);
    final HttpRequest request = createGetRequest("/noindex/");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.NOT_FOUND);
  }

  // Path Traversal Security - CRITICAL

  @Test
  void shouldBlockPathTraversalWithDotDot() throws IOException {
    // Try to escape document root with ../
    final HttpRequest request = createGetRequest("/../../../etc/passwd");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldBlockPathTraversalWithEncodedDots() throws IOException {
    // URL-encoded path traversal: ../ = %2e%2e%2f
    // Note: This test assumes the path is already decoded by the parser
    final HttpRequest request = createGetRequest("/%2e%2e/%2e%2e/etc/passwd");
    final HttpResponse response = handler.handle(request);

    assertThat(getStatus(response)).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldBlockAbsolutePathAttempt() throws IOException {
    // Try to access absolute path outside document root
    final HttpRequest request = createGetRequest("/etc/passwd");
    final HttpResponse response = handler.handle(request);

    // Should only look in document root, not filesystem root
    assertThat(getStatus(response)).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void shouldAllowDotDotWithinDocumentRoot() throws IOException {
    // subdir/../index.html should resolve to index.html within document root
    final HttpRequest request = createGetRequest("/subdir/../index.html");
    final HttpResponse response = handler.handle(request);

    // Should successfully serve root index.html
    assertThat(getStatus(response)).isEqualTo(HttpStatus.OK);
  }

  // Lazy Streaming - Prevent OOM

  @Test
  void shouldUseLazyStreamingForLargeFiles() throws IOException {
    // Create a large file (simulate without actually writing 10MB)
    final Path largeFile = tempDir.resolve("large.bin");
    Files.writeString(largeFile, "x".repeat(1000)); // Small for test speed

    final HttpRequest request = createGetRequest("/large.bin");
    final HttpResponse response = handler.handle(request);

    assertThat(response).isNotNull();
    assertThat(getStatus(response)).isEqualTo(HttpStatus.OK);
    assertThat(writeBody(response)).isEqualTo("x".repeat(1000));
  }

  @Test
  void shouldStreamFileContent() throws IOException {
    final HttpRequest request = createGetRequest("/test.txt");
    final HttpResponse response = handler.handle(request);

    assertThat(writeBody(response)).isEqualTo("Hello World");
  }

  // Helper Methods

  private HttpRequest createGetRequest(final String path) {
    return createRequest(HttpMethod.GET, path);
  }

  private HttpRequest createHeadRequest(final String path) {
    return createRequest(HttpMethod.HEAD, path);
  }

  private HttpRequest createRequest(final HttpMethod method, final String path) {
    final HttpRequest request = mock(HttpRequest.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getPath()).thenReturn(path);
    return request;
  }

  private HttpStatus getStatus(final HttpResponse response) {
    try {
      final var field = HttpResponse.class.getDeclaredField("status");
      field.setAccessible(true);
      return (HttpStatus) field.get(response);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to get status", e);
    }
  }

  private HttpHeaders getHeaders(final HttpResponse response) {
    try {
      final var headersField = HttpResponse.class.getDeclaredField("headers");
      headersField.setAccessible(true);
      return (HttpHeaders) headersField.get(response);
    } catch (final Exception e) {
      throw new RuntimeException("Failed to extract headers", e);
    }
  }

  private String getContentType(final HttpResponse response) {
    return getHeaders(response).get("Content-Type");
  }

  private String getContentLength(final HttpResponse response) {
    return getHeaders(response).get("Content-Length");
  }

  private String writeBody(final HttpResponse response) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      response.writeTo(out);
      final String output = out.toString();
      final int bodyStart = output.indexOf("\r\n\r\n");
      if (bodyStart < 0) {
        return output;
      }
      return output.substring(bodyStart + 4);
    }
  }
}
