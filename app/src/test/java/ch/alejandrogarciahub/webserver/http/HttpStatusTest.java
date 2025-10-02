package ch.alejandrogarciahub.webserver.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HttpStatus} focusing on category classification.
 *
 * <p>Critical: Correct categorization affects error handling, logging, and monitoring. Bugs here
 * could cause incorrect retry logic or missed error alerts.
 */
class HttpStatusTest {

  @Test
  void shouldCorrectlyClassifySuccessStatuses() {
    // Critical: 2xx = success, affects error handling paths
    assertThat(HttpStatus.OK.isSuccess()).isTrue();
    assertThat(HttpStatus.OK.isClientError()).isFalse();
    assertThat(HttpStatus.OK.isServerError()).isFalse();
  }

  @Test
  void shouldCorrectlyClassifyClientErrors() {
    // Critical: 4xx = client error, should NOT retry
    assertThat(HttpStatus.BAD_REQUEST.isClientError()).isTrue();
    assertThat(HttpStatus.NOT_FOUND.isClientError()).isTrue();
    assertThat(HttpStatus.METHOD_NOT_ALLOWED.isClientError()).isTrue();
    assertThat(HttpStatus.PAYLOAD_TOO_LARGE.isClientError()).isTrue();
    assertThat(HttpStatus.URI_TOO_LONG.isClientError()).isTrue();

    assertThat(HttpStatus.BAD_REQUEST.isSuccess()).isFalse();
    assertThat(HttpStatus.BAD_REQUEST.isServerError()).isFalse();
  }

  @Test
  void shouldCorrectlyClassifyServerErrors() {
    // Critical: 5xx = server error, may retry
    assertThat(HttpStatus.INTERNAL_SERVER_ERROR.isServerError()).isTrue();
    assertThat(HttpStatus.NOT_IMPLEMENTED.isServerError()).isTrue();
    assertThat(HttpStatus.HTTP_VERSION_NOT_SUPPORTED.isServerError()).isTrue();

    assertThat(HttpStatus.INTERNAL_SERVER_ERROR.isSuccess()).isFalse();
    assertThat(HttpStatus.INTERNAL_SERVER_ERROR.isClientError()).isFalse();
  }

  @Test
  void shouldMapToCorrectStatusCodes() {
    // Critical: Parser uses these to map exceptions to HTTP responses
    assertThat(HttpStatus.BAD_REQUEST.getCode()).isEqualTo(400);
    assertThat(HttpStatus.PAYLOAD_TOO_LARGE.getCode()).isEqualTo(413);
    assertThat(HttpStatus.URI_TOO_LONG.getCode()).isEqualTo(414);
    assertThat(HttpStatus.NOT_IMPLEMENTED.getCode()).isEqualTo(501);
    assertThat(HttpStatus.HTTP_VERSION_NOT_SUPPORTED.getCode()).isEqualTo(505);
  }
}
