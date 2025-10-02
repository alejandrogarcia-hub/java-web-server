package ch.alejandrogarciahub.webserver;

/**
 * Factory for creating ConnectionHandler instances.
 *
 * <p>This factory pattern enables the WebServer to create a fresh ConnectionHandler for each
 * incoming connection, ensuring thread safety and isolation between concurrent requests.
 *
 * <p><strong>Thread Safety:</strong> Implementations must be thread-safe as they will be called
 * concurrently by multiple virtual threads.
 *
 * @see ConnectionHandler
 */
@FunctionalInterface
public interface ConnectionHandlerFactory {
  /**
   * Creates a new ConnectionHandler instance for handling a single client connection.
   *
   * <p>This method is called for each accepted connection. The returned handler should contain all
   * necessary state and dependencies for processing that connection independently.
   *
   * @return a new ConnectionHandler instance
   */
  ConnectionHandler create();
}
