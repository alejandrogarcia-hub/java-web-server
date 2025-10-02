package ch.alejandrogarciahub.webserver.handler;

import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpResponse;
import java.io.IOException;

/**
 * Strategy interface for handling HTTP requests.
 *
 * <p>Implementations of this interface define how to process HTTP requests and generate responses.
 * This allows different handling strategies (file serving, API endpoints, proxying, etc.) to be
 * plugged into the connection handler.
 *
 * <p><strong>Thread Safety:</strong> Implementations must be thread-safe as they may be called
 * concurrently by multiple virtual threads.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9110.html">RFC 9110 - HTTP Semantics</a>
 */
@FunctionalInterface
public interface HttpRequestHandler {
  /**
   * Handles an HTTP request and generates a response.
   *
   * <p>This method should:
   *
   * <ul>
   *   <li>Process the request based on method, path, headers, and body
   *   <li>Generate an appropriate HTTP response (success or error)
   *   <li>Handle HEAD requests by omitting body (use {@link HttpResponse#writeHeadersOnly})
   *   <li>Set proper Content-Type and Content-Length headers
   *   <li>Return appropriate error status codes (404, 405, 500, etc.)
   * </ul>
   *
   * @param request the HTTP request to handle
   * @return the HTTP response
   * @throws IOException if an I/O error occurs during request handling
   */
  HttpResponse handle(HttpRequest request) throws IOException;
}
