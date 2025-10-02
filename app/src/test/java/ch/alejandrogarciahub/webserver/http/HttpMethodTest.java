package ch.alejandrogarciahub.webserver.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link HttpMethod} focusing on parsing edge cases and RFC compliance.
 *
 * <p>Critical areas: case-insensitive parsing, safety/idempotency classification for caching and
 * security decisions.
 */
class HttpMethodTest {

  @ParameterizedTest
  @ValueSource(strings = {"GET", "get", "GeT", "gEt"})
  void shouldParseCaseInsensitively(final String method) {
    // RFC 9110: Method names are case-sensitive, but we normalize to uppercase
    assertThat(HttpMethod.parse(method)).isEqualTo(HttpMethod.GET);
  }

  @Test
  void shouldRejectUnknownMethod() {
    // Critical: Unknown methods should fail fast, not be silently accepted
    assertThatThrownBy(() -> HttpMethod.parse("INVALID"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown HTTP method");
  }

  @Test
  void shouldRejectNullMethod() {
    assertThatThrownBy(() -> HttpMethod.parse(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectEmptyMethod() {
    assertThatThrownBy(() -> HttpMethod.parse("")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldCorrectlyIdentifySafeMethods() {
    // Critical for caching and security: safe methods must not modify state
    // RFC 9110 Section 9.2.1
    assertThat(HttpMethod.GET.isSafe()).isTrue();
    assertThat(HttpMethod.HEAD.isSafe()).isTrue();
    assertThat(HttpMethod.OPTIONS.isSafe()).isTrue();
    assertThat(HttpMethod.TRACE.isSafe()).isTrue();

    assertThat(HttpMethod.POST.isSafe()).isFalse();
    assertThat(HttpMethod.PUT.isSafe()).isFalse();
    assertThat(HttpMethod.DELETE.isSafe()).isFalse();
  }

  @Test
  void shouldCorrectlyIdentifyIdempotentMethods() {
    // Critical for retry logic: idempotent methods can be safely retried
    // RFC 9110 Section 9.2.2
    assertThat(HttpMethod.GET.isIdempotent()).isTrue();
    assertThat(HttpMethod.PUT.isIdempotent()).isTrue();
    assertThat(HttpMethod.DELETE.isIdempotent()).isTrue();

    assertThat(HttpMethod.POST.isIdempotent()).isFalse();
    assertThat(HttpMethod.PATCH.isIdempotent()).isFalse();
  }
}
