package ch.alejandrogarciahub.webserver.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link HttpHeaders} focusing on RFC 9110 case-insensitive header names.
 *
 * <p>Critical: RFC 9110 Section 5.1 requires case-insensitive header field names. Bugs here break
 * HTTP compliance and cause client compatibility issues.
 */
class HttpHeadersTest {

  @Test
  void shouldTreatHeaderNamesCaseInsensitively() {
    // Critical RFC requirement: "Content-Type", "content-type", "CONTENT-TYPE" are same header
    final HttpHeaders headers = new HttpHeaders();

    headers.set("Content-Type", "text/html");
    assertThat(headers.get("content-type")).isEqualTo("text/html");
    assertThat(headers.get("CONTENT-TYPE")).isEqualTo("text/html");
    assertThat(headers.get("CoNtEnT-TyPe")).isEqualTo("text/html");
  }

  @Test
  void shouldOverwriteExistingHeaderRegardlessOfCase() {
    // Bug risk: Setting "Content-Type" then "content-type" should update, not duplicate
    final HttpHeaders headers = new HttpHeaders();

    headers.set("Content-Type", "text/html");
    headers.set("content-type", "application/json");

    assertThat(headers.get("Content-Type")).isEqualTo("application/json");
    assertThat(headers.size()).isEqualTo(1); // Critical: no duplicate headers
  }

  @Test
  void shouldRemoveHeaderRegardlessOfCase() {
    // Bug risk: remove("content-type") should remove "Content-Type"
    final HttpHeaders headers = new HttpHeaders();

    headers.set("Content-Type", "text/html");
    headers.remove("content-type");

    assertThat(headers.contains("Content-Type")).isFalse();
    assertThat(headers.get("content-type")).isNull();
  }

  @Test
  void shouldCheckContainsRegardlessOfCase() {
    final HttpHeaders headers = new HttpHeaders();

    headers.set("Host", "example.com");

    assertThat(headers.contains("host")).isTrue();
    assertThat(headers.contains("HOST")).isTrue();
    assertThat(headers.contains("HoSt")).isTrue();
  }

  @Test
  void shouldSupportMethodChaining() {
    // Builder pattern: critical for clean response construction
    final HttpHeaders headers =
        new HttpHeaders()
            .set("Content-Type", "text/html")
            .set("Content-Length", "1234")
            .set("Server", "TestServer");

    assertThat(headers.size()).isEqualTo(3);
  }

  @Test
  void shouldHandleEmptyHeaderValue() {
    // RFC allows empty header values
    final HttpHeaders headers = new HttpHeaders();

    headers.set("X-Custom", "");

    assertThat(headers.get("X-Custom")).isEqualTo("");
    assertThat(headers.contains("X-Custom")).isTrue();
  }

  @Test
  void shouldReturnNullForMissingHeader() {
    final HttpHeaders headers = new HttpHeaders();

    assertThat(headers.get("Non-Existent")).isNull();
  }

  @Test
  void shouldReturnAllHeaderNames() {
    final HttpHeaders headers = new HttpHeaders();
    headers.set("Content-Type", "text/html");
    headers.set("Host", "example.com");

    final var names = headers.names();

    assertThat(names).hasSize(2);
    // Case may vary but should contain both headers
    assertThat(names).anySatisfy(name -> assertThat(name.toLowerCase()).isEqualTo("content-type"));
    assertThat(names).anySatisfy(name -> assertThat(name.toLowerCase()).isEqualTo("host"));
  }

  @Test
  void shouldReportCorrectSize() {
    final HttpHeaders headers = new HttpHeaders();

    assertThat(headers.size()).isEqualTo(0);

    headers.set("Content-Type", "text/html");
    assertThat(headers.size()).isEqualTo(1);

    headers.set("Host", "example.com");
    assertThat(headers.size()).isEqualTo(2);

    headers.set("content-type", "application/json"); // Overwrites, doesn't add
    assertThat(headers.size()).isEqualTo(2);
  }

  @Test
  void shouldReportEmptyCorrectly() {
    final HttpHeaders headers = new HttpHeaders();

    assertThat(headers.isEmpty()).isTrue();

    headers.set("Test", "value");
    assertThat(headers.isEmpty()).isFalse();

    headers.remove("test");
    assertThat(headers.isEmpty()).isTrue();
  }
}
