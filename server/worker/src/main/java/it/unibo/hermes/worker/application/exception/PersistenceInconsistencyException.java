package it.unibo.hermes.worker.application.exception;

/**
 * Thrown when the data found in the message store database is inconsistent.
 */
public class PersistenceInconsistencyException extends RuntimeException {

    public PersistenceInconsistencyException(String message) {
        super(message);
    }

    public PersistenceInconsistencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
