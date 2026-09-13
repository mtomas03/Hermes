package it.unibo.hermes.worker.exception;

/**
 * Thrown when Cassandra is unreachable or encounters an unrecoverable error
 * during a persistence operation.
 */
public class PersistenceUnavailableException extends RuntimeException {

    /**
     * Creates a new {@code PersistenceUnavailableException} with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the persistence failure
     * @param cause   the underlying cause of the failure
     */
    public PersistenceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
