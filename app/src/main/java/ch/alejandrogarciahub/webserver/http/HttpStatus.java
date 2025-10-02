package ch.alejandrogarciahub.webserver.http;

/**
 * HTTP status codes as defined in RFC 9110.
 *
 * <p>This enum represents commonly used HTTP status codes. Each status has a numeric code and
 * reason phrase.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-status-codes">RFC 9110 - Status
 *     Codes</a>
 */
public enum HttpStatus {
  // 2xx Success
  OK(200, "OK"),
  CREATED(201, "Created"),
  NO_CONTENT(204, "No Content"),
  NOT_MODIFIED(304, "Not Modified"),

  // 4xx Client Error
  BAD_REQUEST(400, "Bad Request"),
  FORBIDDEN(403, "Forbidden"),
  NOT_FOUND(404, "Not Found"),
  METHOD_NOT_ALLOWED(405, "Method Not Allowed"),
  REQUEST_TIMEOUT(408, "Request Timeout"),
  PAYLOAD_TOO_LARGE(413, "Payload Too Large"),
  URI_TOO_LONG(414, "URI Too Long"),

  // 5xx Server Error
  INTERNAL_SERVER_ERROR(500, "Internal Server Error"),
  NOT_IMPLEMENTED(501, "Not Implemented"),
  SERVICE_UNAVAILABLE(503, "Service Unavailable"),
  HTTP_VERSION_NOT_SUPPORTED(505, "HTTP Version Not Supported");

  private final int code;
  private final String reasonPhrase;

  HttpStatus(final int code, final String reasonPhrase) {
    this.code = code;
    this.reasonPhrase = reasonPhrase;
  }

  /**
   * Returns the numeric status code.
   *
   * @return the status code (e.g., 200, 404)
   */
  public int getCode() {
    return code;
  }

  /**
   * Returns the reason phrase for this status.
   *
   * @return the reason phrase (e.g., "OK", "Not Found")
   */
  public String getReasonPhrase() {
    return reasonPhrase;
  }

  /**
   * Checks if this status indicates success (2xx).
   *
   * @return true if this is a 2xx status code
   */
  public boolean isSuccess() {
    return code >= 200 && code < 300;
  }

  /**
   * Checks if this status indicates a client error (4xx).
   *
   * @return true if this is a 4xx status code
   */
  public boolean isClientError() {
    return code >= 400 && code < 500;
  }

  /**
   * Checks if this status indicates a server error (5xx).
   *
   * @return true if this is a 5xx status code
   */
  public boolean isServerError() {
    return code >= 500 && code < 600;
  }

  @Override
  public String toString() {
    return code + " " + reasonPhrase;
  }
}
