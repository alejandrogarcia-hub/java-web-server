package ch.alejandrogarciahub.webserver.parser;

import ch.alejandrogarciahub.webserver.http.HttpStatus;
import java.io.IOException;

/**
 * Exception thrown when HTTP request parsing fails.
 *
 * <p>This exception encapsulates HTTP parsing errors and associates them with appropriate HTTP
 * status codes for error responses.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9112.html">RFC 9112 - HTTP/1.1</a>
 */
public class HttpParseException extends IOException {
  private final HttpStatus status;

  /**
   * Constructs an HttpParseException with a message and status code.
   *
   * @param message the error message
   * @param status the HTTP status code for the error response
   */
  public HttpParseException(final String message, final HttpStatus status) {
    super(message);
    this.status = status;
  }

  /**
   * Constructs an HttpParseException with a message, status code, and cause.
   *
   * @param message the error message
   * @param status the HTTP status code for the error response
   * @param cause the underlying cause
   */
  public HttpParseException(final String message, final HttpStatus status, final Throwable cause) {
    super(message, cause);
    this.status = status;
  }

  /**
   * Returns the HTTP status code associated with this parse error.
   *
   * @return the HTTP status code
   */
  public HttpStatus getStatus() {
    return status;
  }

  /**
   * Creates a BAD_REQUEST (400) parse exception.
   *
   * @param message the error message
   * @return HttpParseException with 400 status
   */
  public static HttpParseException badRequest(final String message) {
    return new HttpParseException(message, HttpStatus.BAD_REQUEST);
  }

  /**
   * Creates a URI_TOO_LONG (414) parse exception.
   *
   * @param message the error message
   * @return HttpParseException with 414 status
   */
  public static HttpParseException uriTooLong(final String message) {
    return new HttpParseException(message, HttpStatus.URI_TOO_LONG);
  }

  /**
   * Creates a PAYLOAD_TOO_LARGE (413) parse exception.
   *
   * @param message the error message
   * @return HttpParseException with 413 status
   */
  public static HttpParseException payloadTooLarge(final String message) {
    return new HttpParseException(message, HttpStatus.PAYLOAD_TOO_LARGE);
  }

  /**
   * Creates an HTTP_VERSION_NOT_SUPPORTED (505) parse exception.
   *
   * @param message the error message
   * @return HttpParseException with 505 status
   */
  public static HttpParseException httpVersionNotSupported(final String message) {
    return new HttpParseException(message, HttpStatus.HTTP_VERSION_NOT_SUPPORTED);
  }
}
