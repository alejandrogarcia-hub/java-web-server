package ch.alejandrogarciahub.webserver;

import java.net.Socket;

/**
 * Strategy for handling an accepted client connection.
 *
 * <p>This abstraction enables the WebServer to delegate per-connection work to collaborators.
 * Production usage currently relies on a lightweight default implementation, while tests can inject
 * custom handlers to simulate long-running requests or failure scenarios.
 */
@FunctionalInterface
public interface ConnectionHandler {

  void handle(Socket clientSocket) throws Exception;
}
