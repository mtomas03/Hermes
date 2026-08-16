package it.unibo.hermes.gateway.exception;

/**
 * Base runtime exception for all domain errors.
 */
public class HermesException extends RuntimeException {
    public HermesException(String message) {
        super(message);
    }

    public HermesException(String message, Throwable cause) {
        super(message, cause);
    }
}
