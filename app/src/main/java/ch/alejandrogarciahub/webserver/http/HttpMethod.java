package ch.alejandrogarciahub.webserver.http;

/**
 * HTTP request methods as defined in RFC 9110.
 *
 * <p>This enum represents the standard HTTP methods supported by the server. Currently, only GET
 * and HEAD are fully implemented, with other methods defined for future extensibility.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-methods">RFC 9110 - Methods</a>
 */
public enum HttpMethod {
  /** Safe, idempotent method to retrieve a resource representation */
  GET,

  /** Identical to GET but response MUST NOT include message body */
  HEAD,

  /** Submit data to be processed by the identified resource */
  POST,

  /** Replace all current representations of the target resource */
  PUT,

  /** Remove all current representations of the target resource */
  DELETE,

  /** Describe communication options for the target resource */
  OPTIONS,

  /** Perform a message loop-back test along the path to the target resource */
  TRACE,

  /** Establish a tunnel to the server identified by the target resource */
  CONNECT,

  /** Partially modify a resource */
  PATCH;

  /**
   * Parses an HTTP method from a string.
   *
   * <p>HTTP methods are case-sensitive per RFC 9110. This method performs exact case-sensitive
   * matching.
   *
   * @param method the method string (e.g., "GET", "POST")
   * @return the corresponding HttpMethod enum value
   * @throws IllegalArgumentException if the method is not recognized
   */
  public static HttpMethod parse(final String method) {
    try {
      return HttpMethod.valueOf(method.toUpperCase());
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown HTTP method: " + method, e);
    }
  }

  /**
   * Checks if this method is safe (does not modify server state).
   *
   * <p>Safe methods per RFC 9110: GET, HEAD, OPTIONS, TRACE
   *
   * @return true if this method is safe
   */
  public boolean isSafe() {
    return this == GET || this == HEAD || this == OPTIONS || this == TRACE;
  }

  /**
   * Checks if this method is idempotent (multiple identical requests have the same effect).
   *
   * <p>Idempotent methods per RFC 9110: GET, HEAD, PUT, DELETE, OPTIONS, TRACE
   *
   * @return true if this method is idempotent
   */
  public boolean isIdempotent() {
    return this != POST && this != CONNECT && this != PATCH;
  }
}
