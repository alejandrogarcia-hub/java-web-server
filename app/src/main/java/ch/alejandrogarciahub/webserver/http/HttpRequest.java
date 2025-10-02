package ch.alejandrogarciahub.webserver.http;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable representation of an HTTP/1.1 request.
 *
 * <p>This class encapsulates all components of an HTTP request: method, URI, headers, and body. All
 * fields are final and the class is thread-safe.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9112.html">RFC 9112 - HTTP/1.1</a>
 */
public final class HttpRequest {
  private final HttpMethod method;
  private final String requestTarget;
  private final String path;
  private final Map<String, String> queryParams;
  private final HttpVersion version;
  private final HttpHeaders headers;
  private final byte[] body;

  /**
   * Constructs an HttpRequest.
   *
   * @param method the HTTP method
   * @param requestTarget the raw request-target (URI)
   * @param version the HTTP version
   * @param headers the HTTP headers
   * @param body the request body (empty array if no body)
   */
  public HttpRequest(
      final HttpMethod method,
      final String requestTarget,
      final HttpVersion version,
      final HttpHeaders headers,
      final byte[] body) {
    this.method = method;
    this.requestTarget = requestTarget;
    this.version = version;
    this.headers = headers;
    this.body = body.clone(); // Defensive copy

    // Parse URI components
    try {
      final URI uri = new URI(requestTarget);
      this.path = uri.getPath() != null ? uri.getPath() : "/";
      this.queryParams = parseQueryString(uri.getRawQuery());
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException("Invalid request-target: " + requestTarget, e);
    }
  }

  /**
   * Parses query string into parameter map.
   *
   * @param query the raw query string (null if no query)
   * @return unmodifiable map of query parameters
   */
  private Map<String, String> parseQueryString(final String query) {
    if (query == null || query.isEmpty()) {
      return Collections.emptyMap();
    }

    final Map<String, String> params = new LinkedHashMap<>();
    for (final String pair : query.split("&")) {
      final int idx = pair.indexOf('=');
      if (idx > 0) {
        try {
          final String key =
              URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8.name());
          final String value =
              URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8.name());
          params.put(key, value);
        } catch (final UnsupportedEncodingException e) {
          // UTF-8 is always supported
          throw new AssertionError("UTF-8 encoding not supported", e);
        }
      }
    }
    return Collections.unmodifiableMap(params);
  }

  /**
   * Returns the HTTP method.
   *
   * @return the HTTP method
   */
  public HttpMethod getMethod() {
    return method;
  }

  /**
   * Returns the raw request-target (URI).
   *
   * @return the request-target
   */
  public String getRequestTarget() {
    return requestTarget;
  }

  /**
   * Returns the parsed path component of the URI.
   *
   * @return the path (e.g., "/index.html")
   */
  public String getPath() {
    return path;
  }

  /**
   * Returns the query parameters.
   *
   * @return unmodifiable map of query parameters
   */
  public Map<String, String> getQueryParams() {
    return queryParams;
  }

  /**
   * Gets a single query parameter value.
   *
   * @param name the parameter name
   * @return the parameter value, or null if not present
   */
  public String getQueryParam(final String name) {
    return queryParams.get(name);
  }

  /**
   * Returns the HTTP version.
   *
   * @return the HTTP version
   */
  public HttpVersion getVersion() {
    return version;
  }

  /**
   * Returns the HTTP headers.
   *
   * @return the headers
   */
  public HttpHeaders getHeaders() {
    return headers;
  }

  /**
   * Gets a single header value.
   *
   * @param name the header name (case-insensitive)
   * @return the header value, or null if not present
   */
  public String getHeader(final String name) {
    return headers.get(name);
  }

  /**
   * Returns the request body.
   *
   * @return defensive copy of the body bytes
   */
  public byte[] getBody() {
    return body.clone();
  }

  /**
   * Returns the Content-Length header value.
   *
   * @return the content length, or 0 if not present
   */
  public long getContentLength() {
    final String value = headers.get("Content-Length");
    return value != null ? Long.parseLong(value) : 0;
  }

  /**
   * Checks if the request uses chunked transfer encoding.
   *
   * @return true if Transfer-Encoding: chunked
   */
  public boolean isChunked() {
    return "chunked".equalsIgnoreCase(headers.get("Transfer-Encoding"));
  }

  /**
   * Determines if the connection should be kept alive (persistent).
   *
   * <p>HTTP/1.1 defaults to keep-alive unless Connection: close is present. HTTP/1.0 requires
   * explicit Connection: keep-alive.
   *
   * @return true if the connection should be kept alive
   */
  public boolean isKeepAlive() {
    final String connection = headers.get("Connection");

    if (version == HttpVersion.HTTP_1_1) {
      // HTTP/1.1: persistent by default
      return !"close".equalsIgnoreCase(connection);
    } else {
      // HTTP/1.0: only if explicitly requested
      return "keep-alive".equalsIgnoreCase(connection);
    }
  }

  @Override
  public String toString() {
    return String.format(
        "HttpRequest{method=%s, path=%s, version=%s, headers=%d, bodySize=%d}",
        method, path, version, headers.size(), body.length);
  }
}
