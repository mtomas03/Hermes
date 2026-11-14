package it.unibo.hermes.client.infrastructure.exception;

/**
 * Thrown when a local SQLite persistence operation fails.
 */
public class SQLitePersistenceException extends RuntimeException {

    public SQLitePersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
