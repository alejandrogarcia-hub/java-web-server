package ch.alejandrogarciahub.webserver.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HttpVersion} focusing on keep-alive defaults and version parsing.
 *
 * <p>Critical: Incorrect keep-alive defaults cause connection leaks or premature closes. Version
 * parsing bugs could accept invalid protocols or reject valid ones.
 */
class HttpVersionTest {

  @Test
  void shouldParseHttp11() {
    assertThat(HttpVersion.parse("HTTP/1.1")).isEqualTo(HttpVersion.HTTP_1_1);
  }

  @Test
  void shouldParseHttp10() {
    assertThat(HttpVersion.parse("HTTP/1.0")).isEqualTo(HttpVersion.HTTP_1_0);
  }

  @Test
  void shouldDefaultToKeepAliveHttp11() {
    // Critical: HTTP/1.1 is persistent by default (RFC 9112)
    // Bug here causes connection to close when it should persist
    assertThat(HttpVersion.HTTP_1_1.defaultsToKeepAlive()).isTrue();
  }

  @Test
  void shouldNotDefaultToKeepAliveHttp10() {
    // Critical: HTTP/1.0 is non-persistent by default (RFC 1945)
    // Bug here causes connection leak when client expects close
    assertThat(HttpVersion.HTTP_1_0.defaultsToKeepAlive()).isFalse();
  }

  @Test
  void shouldRejectUnsupportedVersionHttp20() {
    // Security: Reject HTTP/2.0 sent as HTTP/1.x (protocol confusion)
    assertThatThrownBy(() -> HttpVersion.parse("HTTP/2.0"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported HTTP version");
  }

  @Test
  void shouldRejectMalformedVersion() {
    assertThatThrownBy(() -> HttpVersion.parse("HTTP1.1"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> HttpVersion.parse("HTTP/1.2"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectNullVersion() {
    // Current implementation throws IllegalArgumentException, not NPE
    // This is acceptable - the important thing is it fails fast
    assertThatThrownBy(() -> HttpVersion.parse(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unsupported HTTP version");
  }
}
