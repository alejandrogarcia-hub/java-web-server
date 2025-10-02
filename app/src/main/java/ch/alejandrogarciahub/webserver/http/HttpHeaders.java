package ch.alejandrogarciahub.webserver.http;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Case-insensitive HTTP header map.
 *
 * <p>HTTP header field names are case-insensitive per RFC 9110. This class provides a wrapper
 * around a TreeMap with case-insensitive key comparison.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-field-names">RFC 9110 - Field
 *     Names</a>
 */
public final class HttpHeaders {
  private final Map<String, String> headers;

  /** Constructs an empty HttpHeaders instance. */
  public HttpHeaders() {
    this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
  }

  /**
   * Constructs HttpHeaders from an existing map.
   *
   * @param headers the headers map (will be copied with case-insensitive keys)
   */
  public HttpHeaders(final Map<String, String> headers) {
    this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    this.headers.putAll(headers);
  }

  /**
   * Sets a header field.
   *
   * @param name the header field name (case-insensitive)
   * @param value the header field value
   * @return this HttpHeaders instance for method chaining
   */
  public HttpHeaders set(final String name, final String value) {
    headers.put(name, value);
    return this;
  }

  /**
   * Gets a header field value.
   *
   * @param name the header field name (case-insensitive)
   * @return the header field value, or null if not present
   */
  public String get(final String name) {
    return headers.get(name);
  }

  /**
   * Gets a header field value with a default.
   *
   * @param name the header field name (case-insensitive)
   * @param defaultValue the default value if header is not present
   * @return the header field value, or defaultValue if not present
   */
  public String getOrDefault(final String name, final String defaultValue) {
    return headers.getOrDefault(name, defaultValue);
  }

  /**
   * Checks if a header field is present.
   *
   * @param name the header field name (case-insensitive)
   * @return true if the header is present
   */
  public boolean contains(final String name) {
    return headers.containsKey(name);
  }

  /**
   * Removes a header field.
   *
   * @param name the header field name (case-insensitive)
   * @return the previous value, or null if not present
   */
  public String remove(final String name) {
    return headers.remove(name);
  }

  /**
   * Returns the number of headers.
   *
   * @return the header count
   */
  public int size() {
    return headers.size();
  }

  /**
   * Checks if there are no headers.
   *
   * @return true if there are no headers
   */
  public boolean isEmpty() {
    return headers.isEmpty();
  }

  /**
   * Returns all header field names.
   *
   * @return unmodifiable set of header names
   */
  public Set<String> names() {
    return Collections.unmodifiableSet(headers.keySet());
  }

  /**
   * Returns an unmodifiable view of the headers map.
   *
   * @return unmodifiable map of headers
   */
  public Map<String, String> asMap() {
    return Collections.unmodifiableMap(headers);
  }

  /** Clears all headers. */
  public void clear() {
    headers.clear();
  }

  @Override
  public String toString() {
    return headers.toString();
  }
}
