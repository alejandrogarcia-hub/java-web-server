package ch.alejandrogarciahub.webserver.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpResponse;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import ch.alejandrogarciahub.webserver.http.HttpVersion;
import ch.alejandrogarciahub.webserver.observability.AccessLogger;
import ch.alejandrogarciahub.webserver.observability.HttpMetrics;
import ch.alejandrogarciahub.webserver.observability.ObservabilityConfig;
import ch.alejandrogarciahub.webserver.parser.HttpParseException;
import ch.alejandrogarciahub.webserver.parser.HttpRequestParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Tests for {@link HttpConnectionHandler} focusing on keep-alive loop and error handling.
 *
 * <p>Critical: Keep-alive bugs cause connection leaks or premature closes. Error handling bugs
 * expose stack traces or hang connections.
 */
class HttpConnectionHandlerTest {

  private HttpRequestHandler mockRequestHandler;
  private HttpRequestParser mockParser;
  private HttpConnectionHandler handler;
  private Socket mockSocket;
  private ByteArrayOutputStream outputStream;
  private HttpMetrics mockMetrics;
  private ObservabilityConfig observabilityConfig;
  private AccessLogger accessLogger;

  @BeforeEach
  void setup() throws IOException {
    mockRequestHandler = mock(HttpRequestHandler.class);
    mockParser = mock(HttpRequestParser.class);
    mockMetrics = mock(HttpMetrics.class);
    observabilityConfig = new ObservabilityConfig(true, true, -1, "/metrics");
    accessLogger = mock(AccessLogger.class);
    handler =
        new HttpConnectionHandler(
            mockRequestHandler, mockParser, 5000, mockMetrics, observabilityConfig, accessLogger);

    mockSocket = mock(Socket.class);
    outputStream = new ByteArrayOutputStream();

    when(mockSocket.getRemoteSocketAddress()).thenReturn(mock(java.net.SocketAddress.class));
    when(mockSocket.getOutputStream()).thenReturn(outputStream);
    when(mockSocket.isClosed()).thenReturn(false);
  }

  // Keep-Alive Loop - Critical for Connection Management

  @Test
  void shouldHandleSingleRequestAndClose() throws IOException {
    // HTTP/1.1 request with Connection: close
    final HttpRequest request = createRequest(HttpMethod.GET, "/", HttpVersion.HTTP_1_1, false);
    final HttpResponse response = new HttpResponse().status(HttpStatus.OK);

    when(mockSocket.getInputStream())
        .thenReturn(new ByteArrayInputStream("GET / HTTP/1.1\r\n\r\n".getBytes()));
    when(mockParser.parse(any(InputStream.class))).thenReturn(request).thenReturn(null);
    when(mockRequestHandler.handle(request)).thenReturn(response);

    handler.handle(mockSocket);

    verify(mockSocket).close();
    verify(mockMetrics).connectionOpened();
    verify(mockMetrics).connectionClosed();
    verify(mockMetrics)
        .recordRequest(
            Mockito.eq(HttpMethod.GET),
            Mockito.eq(HttpStatus.OK),
            Mockito.anyLong(),
            Mockito.anyLong());
  }

  @Test
  void shouldHandleMultipleRequestsWithKeepAlive() throws IOException {
    // HTTP/1.1 requests with keep-alive (default)
    final HttpRequest request1 = createRequest(HttpMethod.GET, "/", HttpVersion.HTTP_1_1, true);
    final HttpRequest request2 = createRequest(HttpMethod.GET, "/test", HttpVersion.HTTP_1_1, true);
    final HttpRequest request3 = createRequest(HttpMethod.GET, "/end", HttpVersion.HTTP_1_1, false);

    final HttpResponse response = new HttpResponse().status(HttpStatus.OK);

    when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("requests".getBytes()));
    when(mockParser.parse(any(InputStream.class)))
        .thenReturn(request1)
        .thenReturn(request2)
        .thenReturn(request3)
        .thenReturn(null);
    when(mockRequestHandler.handle(any())).thenReturn(response);

    handler.handle(mockSocket);

    verify(mockRequestHandler, Mockito.times(3)).handle(any());
    verify(mockSocket).close();
    verify(mockMetrics, Mockito.atLeastOnce())
        .recordRequest(any(), any(), Mockito.anyLong(), Mockito.anyLong());
  }

  @Test
  void shouldCloseOnGracefulEof() throws IOException {
    // Parser returns null on graceful EOF (client closed connection cleanly)
    // This is not an error - no request was made, so no metrics should be recorded
    when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
    when(mockParser.parse(any(InputStream.class))).thenReturn(null);

    handler.handle(mockSocket);

    verify(mockSocket).close();
    verify(mockMetrics).connectionOpened();
    verify(mockMetrics).connectionClosed();
    verifyNoMoreInteractions(accessLogger);
  }

  @Test
  void shouldCloseOnParseException() throws IOException {
    // Parser throws HttpParseException
    final HttpParseException parseError = HttpParseException.badRequest("Invalid request");

    when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("invalid".getBytes()));
    when(mockParser.parse(any(InputStream.class))).thenThrow(parseError);

    handler.handle(mockSocket);

    // Should send error response and close
    verify(mockSocket).close();
    final String output = outputStream.toString();
    assertThat(output).contains("400");
    final ArgumentCaptor<AccessLogger.Entry> logCaptor =
        ArgumentCaptor.forClass(AccessLogger.Entry.class);
    verify(accessLogger).log(logCaptor.capture());
    assertThat(logCaptor.getValue().status()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    verify(mockMetrics)
        .recordRequest(
            Mockito.eq(null),
            Mockito.eq(HttpStatus.BAD_REQUEST),
            Mockito.anyLong(),
            Mockito.anyLong());
  }

  @Test
  void shouldCloseOnSocketTimeout() throws IOException {
    when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("slow".getBytes()));
    when(mockParser.parse(any(InputStream.class))).thenThrow(new SocketTimeoutException());

    handler.handle(mockSocket);

    verify(mockSocket).close();
    final ArgumentCaptor<AccessLogger.Entry> logCaptor =
        ArgumentCaptor.forClass(AccessLogger.Entry.class);
    verify(accessLogger).log(logCaptor.capture());
    assertThat(logCaptor.getValue().status()).isEqualTo(HttpStatus.REQUEST_TIMEOUT.getCode());
    assertThat(logCaptor.getValue().durationMillis()).isGreaterThanOrEqualTo(0L);
    verify(mockMetrics)
        .recordRequest(
            Mockito.eq(null),
            Mockito.eq(HttpStatus.REQUEST_TIMEOUT),
            Mockito.anyLong(),
            Mockito.eq(0L));
  }

  @Test
  void shouldCloseOnIoException() throws IOException {
    when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("error".getBytes()));
    when(mockParser.parse(any(InputStream.class))).thenThrow(new IOException("Network error"));

    handler.handle(mockSocket);

    verify(mockSocket).close();
    final String output = outputStream.toString();
    assertThat(output).contains("500");
    final ArgumentCaptor<AccessLogger.Entry> logCaptor =
        ArgumentCaptor.forClass(AccessLogger.Entry.class);
    verify(accessLogger).log(logCaptor.capture());
    assertThat(logCaptor.getValue().status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
    verify(mockMetrics)
        .recordRequest(
            Mockito.eq(null),
            Mockito.eq(HttpStatus.INTERNAL_SERVER_ERROR),
            Mockito.anyLong(),
            Mockito.anyLong());
  }

  @Test
  void shouldCloseOnUnexpectedException() throws IOException {
    when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("error".getBytes()));
    when(mockParser.parse(any(InputStream.class))).thenThrow(new RuntimeException("Unexpected"));

    handler.handle(mockSocket);

    verify(mockSocket).close();
    final String output = outputStream.toString();
    assertThat(output).contains("500");
    final ArgumentCaptor<AccessLogger.Entry> logCaptor =
        ArgumentCaptor.forClass(AccessLogger.Entry.class);
    verify(accessLogger).log(logCaptor.capture());
    assertThat(logCaptor.getValue().status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.getCode());
  }

  // HEAD Method Support

  @Test
  void shouldWriteHeadersOnlyForHeadRequest() throws IOException {
    final HttpRequest request = createRequest(HttpMethod.HEAD, "/", HttpVersion.HTTP_1_1, false);
    final HttpResponse response = new HttpResponse().status(HttpStatus.OK).body("body content");

    when(mockSocket.getInputStream())
        .thenReturn(new ByteArrayInputStream("HEAD / HTTP/1.1\r\n\r\n".getBytes()));
    when(mockParser.parse(any(InputStream.class))).thenReturn(request).thenReturn(null);
    when(mockRequestHandler.handle(request)).thenReturn(response);

    handler.handle(mockSocket);

    final String output = outputStream.toString();
    assertThat(output).contains("200 OK");
    assertThat(output).contains("Content-Length: 12");
    assertThat(output).doesNotContain("body content"); // No body for HEAD
  }

  @Test
  void shouldWriteFullResponseForGetRequest() throws IOException {
    final HttpRequest request = createRequest(HttpMethod.GET, "/", HttpVersion.HTTP_1_1, false);
    final HttpResponse response = new HttpResponse().status(HttpStatus.OK).body("body content");

    when(mockSocket.getInputStream())
        .thenReturn(new ByteArrayInputStream("GET / HTTP/1.1\r\n\r\n".getBytes()));
    when(mockParser.parse(any(InputStream.class))).thenReturn(request).thenReturn(null);
    when(mockRequestHandler.handle(request)).thenReturn(response);

    handler.handle(mockSocket);

    final String output = outputStream.toString();
    assertThat(output).contains("200 OK");
    assertThat(output).endsWith("body content");
  }

  // Connection Directive Priority

  @Test
  void shouldRespectHandlerConnectionDirective() throws IOException {
    // Request wants keep-alive, but handler forces close
    final HttpRequest request = createRequest(HttpMethod.GET, "/", HttpVersion.HTTP_1_1, true);
    final HttpResponse response =
        new HttpResponse().status(HttpStatus.OK).header("Connection", "close");

    when(mockSocket.getInputStream())
        .thenReturn(new ByteArrayInputStream("GET / HTTP/1.1\r\n\r\n".getBytes()));
    when(mockParser.parse(any(InputStream.class))).thenReturn(request).thenReturn(null);
    when(mockRequestHandler.handle(request)).thenReturn(response);

    handler.handle(mockSocket);

    // Should close despite client's keep-alive preference
    verify(mockSocket).close();
  }

  @Test
  void shouldUseRequestKeepAliveWhenHandlerHasNoDirective() throws IOException {
    // Handler doesn't set Connection header, use client's preference
    final HttpRequest request = createRequest(HttpMethod.GET, "/", HttpVersion.HTTP_1_1, true);
    final HttpResponse response = new HttpResponse().status(HttpStatus.OK);

    // Simulate multiple requests
    when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream("requests".getBytes()));
    when(mockParser.parse(any(InputStream.class)))
        .thenReturn(request)
        .thenReturn(request)
        .thenReturn(null); // graceful exit
    when(mockRequestHandler.handle(request)).thenReturn(response);

    handler.handle(mockSocket);

    // Should handle multiple requests since keep-alive is true
    verify(mockRequestHandler, Mockito.atLeast(2)).handle(request);
    verify(mockParser, Mockito.times(3)).parse(any(InputStream.class));
  }

  // Version Matching

  @Test
  void shouldMatchResponseVersionToRequest() throws IOException {
    final HttpRequest request = createRequest(HttpMethod.GET, "/", HttpVersion.HTTP_1_0, false);
    final HttpResponse response = new HttpResponse().status(HttpStatus.OK);

    when(mockSocket.getInputStream())
        .thenReturn(new ByteArrayInputStream("GET / HTTP/1.0\r\n\r\n".getBytes()));
    when(mockParser.parse(any(InputStream.class))).thenReturn(request).thenReturn(null);
    when(mockRequestHandler.handle(request)).thenReturn(response);

    handler.handle(mockSocket);

    final String output = outputStream.toString();
    assertThat(output).startsWith("HTTP/1.0");
  }

  // Helper Methods

  private HttpRequest createRequest(
      final HttpMethod method,
      final String path,
      final HttpVersion version,
      final boolean keepAlive) {
    final HttpRequest request = mock(HttpRequest.class);
    when(request.getMethod()).thenReturn(method);
    when(request.getPath()).thenReturn(path);
    when(request.getVersion()).thenReturn(version);
    when(request.isKeepAlive()).thenReturn(keepAlive);
    when(request.getQueryParams()).thenReturn(java.util.Collections.emptyMap());
    when(request.getHeader("X-Request-Id")).thenReturn(null);
    return request;
  }
}
