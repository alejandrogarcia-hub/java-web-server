package ch.alejandrogarciahub.webserver.parser;

import static org.assertj.core.api.Assertions.assertThat;

import ch.alejandrogarciahub.webserver.http.HttpStatus;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HttpParseException} focusing on status code mapping.
 *
 * <p>Critical: Parser exceptions must map to correct HTTP status codes for proper error responses.
 */
class HttpParseExceptionTest {

  @Test
  void shouldMapBadRequestTo400() {
    final HttpParseException ex = HttpParseException.badRequest("Invalid input");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(ex.getMessage()).contains("Invalid input");
  }

  @Test
  void shouldMapUriTooLongTo414() {
    final HttpParseException ex = HttpParseException.uriTooLong("URI exceeds limit");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.URI_TOO_LONG);
    assertThat(ex.getMessage()).contains("URI exceeds limit");
  }

  @Test
  void shouldMapPayloadTooLargeTo413() {
    final HttpParseException ex = HttpParseException.payloadTooLarge("Body too large");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    assertThat(ex.getMessage()).contains("Body too large");
  }

  @Test
  void shouldMapHttpVersionNotSupportedTo505() {
    final HttpParseException ex = HttpParseException.httpVersionNotSupported("Unsupported version");

    assertThat(ex.getStatus()).isEqualTo(HttpStatus.HTTP_VERSION_NOT_SUPPORTED);
    assertThat(ex.getMessage()).contains("Unsupported version");
  }

  @Test
  void shouldPreserveCauseException() {
    final Exception cause = new RuntimeException("Root cause");
    final HttpParseException ex =
        new HttpParseException("Parse error", HttpStatus.BAD_REQUEST, cause);

    assertThat(ex.getCause()).isEqualTo(cause);
  }
}
