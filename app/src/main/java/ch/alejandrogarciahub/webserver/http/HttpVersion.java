package ch.alejandrogarciahub.webserver.http;

/**
 * HTTP protocol versions as defined in RFC 9112.
 *
 * <p>This enum represents the HTTP protocol versions supported by the server. Currently supports
 * HTTP/1.0 and HTTP/1.1.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9112.html">RFC 9112 - HTTP/1.1</a>
 */
public enum HttpVersion {
  /** HTTP/1.0 - Non-persistent connections by default */
  HTTP_1_0("HTTP/1.0"),

  /** HTTP/1.1 - Persistent connections by default, chunked encoding support */
  HTTP_1_1("HTTP/1.1");

  private final String value;

  HttpVersion(final String value) {
    this.value = value;
  }

  /**
   * Returns the string representation of this HTTP version.
   *
   * @return the version string (e.g., "HTTP/1.1")
   */
  public String getValue() {
    return value;
  }

  /**
   * Parses an HTTP version from a string.
   *
   * <p>HTTP version strings are case-sensitive per RFC 9112.
   *
   * @param version the version string (e.g., "HTTP/1.1")
   * @return the corresponding HttpVersion enum value
   * @throws IllegalArgumentException if the version is not supported
   */
  public static HttpVersion parse(final String version) {
    for (final HttpVersion v : values()) {
      if (v.value.equals(version)) {
        return v;
      }
    }
    throw new IllegalArgumentException("Unsupported HTTP version: " + version);
  }

  /**
   * Checks if this version defaults to persistent connections (keep-alive).
   *
   * <p>HTTP/1.1 defaults to persistent connections, HTTP/1.0 does not.
   *
   * @return true if persistent connections are the default
   */
  public boolean defaultsToKeepAlive() {
    return this == HTTP_1_1;
  }

  @Override
  public String toString() {
    return value;
  }
}
