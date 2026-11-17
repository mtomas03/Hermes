package it.unibo.hermes.worker.application.exception;

/**
 * Thrown when the worker fails to persist a message or its delivery status.
 */
public class PersistenceUnavailableException extends RuntimeException {

    public PersistenceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
