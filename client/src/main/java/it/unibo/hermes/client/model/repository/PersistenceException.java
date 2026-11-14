package it.unibo.hermes.client.model.repository;

/**
 * Thrown when a local SQLite persistence operation fails.
 */
public class PersistenceException extends RuntimeException {

    public PersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
