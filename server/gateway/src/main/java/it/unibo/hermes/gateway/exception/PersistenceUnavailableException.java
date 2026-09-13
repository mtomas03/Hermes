package it.unibo.hermes.gateway.exception;

/**
 * Thrown when Cassandra is unreachable.
 *
 * <p> When both Kafka and Cassandra are unavailable,
 * the gateway rejects the message submission rather than silently losing it.
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
