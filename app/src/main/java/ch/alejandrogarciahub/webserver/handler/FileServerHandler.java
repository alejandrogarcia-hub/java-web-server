package ch.alejandrogarciahub.webserver.handler;

import ch.alejandrogarciahub.webserver.http.HttpMethod;
import ch.alejandrogarciahub.webserver.http.HttpRequest;
import ch.alejandrogarciahub.webserver.http.HttpResponse;
import ch.alejandrogarciahub.webserver.http.HttpStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP request handler for serving static files.
 *
 * <p>This handler serves files from a configured document root directory. It supports:
 *
 * <ul>
 *   <li>GET and HEAD methods
 *   <li>Automatic MIME type detection based on file extension
 *   <li>Index file serving (index.html for directory requests)
 *   <li>Path traversal prevention (security)
 *   <li>404 Not Found for missing files
 *   <li>405 Method Not Allowed for unsupported methods
 * </ul>
 *
 * <p><strong>Configuration (Environment Variables):</strong>
 *
 * <ul>
 *   <li>{@code DOCUMENT_ROOT} - Root directory for serving files (default: "./public")
 * </ul>
 *
 * <p><strong>Security:</strong> This handler validates that requested paths remain within the
 * document root to prevent path traversal attacks (e.g., {@code ../../etc/passwd}).
 *
 * <p><strong>Thread Safety:</strong> This handler is thread-safe and can be shared across multiple
 * connections.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-get">RFC 9110 - GET Method</a>
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9110.html#name-head">RFC 9110 - HEAD Method</a>
 */
public final class FileServerHandler implements HttpRequestHandler {
  private static final Logger logger = LoggerFactory.getLogger(FileServerHandler.class);

  private final Path documentRoot;
  private static final String DEFAULT_INDEX = "index.html";

  /**
   * Constructs a FileServerHandler with explicit document root.
   *
   * @param documentRoot the root directory for serving files
   */
  public FileServerHandler(final Path documentRoot) {
    this.documentRoot = documentRoot.toAbsolutePath().normalize();
    logger.info("Serving files from: {}", this.documentRoot);

    // Create document root if it doesn't exist
    try {
      if (!Files.exists(this.documentRoot)) {
        Files.createDirectories(this.documentRoot);
        logger.info("Created document root directory: {}", this.documentRoot);
      }
    } catch (final IOException e) {
      logger.error("Failed to create document root directory: {}", this.documentRoot, e);
    }
  }

  /**
   * Handles HTTP requests for static files.
   *
   * <p>Supports GET and HEAD methods. Returns:
   *
   * <ul>
   *   <li>200 OK - File found and served
   *   <li>404 Not Found - File doesn't exist or outside document root
   *   <li>405 Method Not Allowed - Method other than GET/HEAD
   * </ul>
   *
   * @param request the HTTP request
   * @return the HTTP response
   * @throws IOException if an I/O error occurs reading the file
   */
  @Override
  public HttpResponse handle(final HttpRequest request) throws IOException {
    final HttpMethod method = request.getMethod();

    // Only support GET and HEAD
    if (method != HttpMethod.GET && method != HttpMethod.HEAD) {
      return HttpResponse.methodNotAllowed("GET, HEAD");
    }

    // Resolve requested path
    final Path requestedPath = resolvePath(request.getPath());
    if (requestedPath == null) {
      // Path traversal attempt or invalid path
      logger.warn("Path traversal or invalid path attempt: {}", request.getPath());
      return HttpResponse.notFound();
    }

    // Check if path exists
    if (!Files.exists(requestedPath)) {
      logger.debug("File not found: {}", requestedPath);
      return HttpResponse.notFound();
    }

    // Handle directory requests - serve index.html
    final Path targetFile;
    if (Files.isDirectory(requestedPath)) {
      targetFile = requestedPath.resolve(DEFAULT_INDEX);
      if (!Files.exists(targetFile)) {
        logger.debug("Directory index not found: {}", targetFile);
        return HttpResponse.notFound();
      }
    } else {
      targetFile = requestedPath;
    }

    // Read file content
    final long contentLength = Files.size(targetFile);
    final String contentType = detectContentType(targetFile);

    logger.debug("Serving file: {} ({} bytes, {})", targetFile, contentLength, contentType);

    final HttpResponse response =
        new HttpResponse().status(HttpStatus.OK).contentType(contentType).bodyLength(contentLength);

    // Lazy file streaming: Instead of loading the entire file into memory with
    // Files.readAllBytes(),
    // we provide a supplier that opens an InputStream when HttpResponse.writeTo() is called.
    // This allows serving large files (videos, images, archives) efficiently without risk of
    // OutOfMemoryError, especially under concurrent load. The file is streamed directly from
    // disk to socket using InputStream.transferTo().
    response.setBodySupplier(() -> Files.newInputStream(targetFile));
    return response;
  }

  /**
   * Resolves a request path to an actual file path within the document root.
   *
   * <p>This method performs security validation to prevent path traversal attacks. It ensures the
   * resolved path is within the document root.
   *
   * @param requestPath the request path (e.g., "/index.html", "/css/style.css")
   * @return the resolved absolute path, or null if invalid/outside document root
   */
  private Path resolvePath(final String requestPath) {
    try {
      // Remove leading slash and resolve relative to document root
      String cleanPath = requestPath;
      if (cleanPath.startsWith("/")) {
        cleanPath = cleanPath.substring(1);
      }

      // Handle root path
      if (cleanPath.isEmpty()) {
        cleanPath = ".";
      }

      final Path resolved = documentRoot.resolve(cleanPath).toAbsolutePath().normalize();

      // Security: Verify resolved path is within document root
      if (!resolved.startsWith(documentRoot)) {
        logger.warn("Path traversal attempt detected: {} -> {}", requestPath, resolved);
        return null;
      }

      return resolved;

    } catch (final Exception e) {
      logger.warn("Error resolving path: {}", requestPath, e);
      return null;
    }
  }

  /**
   * Detects MIME content type based on file extension.
   *
   * <p>Uses Java's {@link Files#probeContentType} as primary detection, with fallback to
   * extension-based mapping for common web file types.
   *
   * @param filePath the file path
   * @return the MIME type (defaults to "application/octet-stream" if unknown)
   */
  private String detectContentType(final Path filePath) {
    try {
      // Try Java's built-in content type detection
      final String probed = Files.probeContentType(filePath);
      if (probed != null) {
        return probed;
      }
    } catch (final IOException e) {
      logger.debug("Failed to probe content type for {}: {}", filePath, e.getMessage());
    }

    // Fallback to extension-based detection
    final String fileName = filePath.getFileName().toString().toLowerCase();
    if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
      return "text/html; charset=UTF-8";
    } else if (fileName.endsWith(".css")) {
      return "text/css; charset=UTF-8";
    } else if (fileName.endsWith(".js")) {
      return "text/javascript; charset=UTF-8";
    } else if (fileName.endsWith(".json")) {
      return "application/json; charset=UTF-8";
    } else if (fileName.endsWith(".xml")) {
      return "application/xml; charset=UTF-8";
    } else if (fileName.endsWith(".txt")) {
      return "text/plain; charset=UTF-8";
    } else if (fileName.endsWith(".png")) {
      return "image/png";
    } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
      return "image/jpeg";
    } else if (fileName.endsWith(".gif")) {
      return "image/gif";
    } else if (fileName.endsWith(".svg")) {
      return "image/svg+xml";
    } else if (fileName.endsWith(".ico")) {
      return "image/x-icon";
    } else if (fileName.endsWith(".pdf")) {
      return "application/pdf";
    } else if (fileName.endsWith(".zip")) {
      return "application/zip";
    }

    // Default for unknown types
    return "application/octet-stream";
  }
}
