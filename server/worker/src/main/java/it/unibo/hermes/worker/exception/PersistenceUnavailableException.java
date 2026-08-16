package it.unibo.hermes.worker.exception;

import it.unibo.hermes.worker.config.KafkaConfig;

/**
 * Thrown when the Cassandra database is unreachable or encounters an unrecoverable error
 * during a persistence operation.
 *
 * <p> A message must never be marked as delivered if it cannot be persisted.
 * This exception is intended to propagate to the Kafka listener,
 * triggering the bounded retry mechanism configured in {@link KafkaConfig}.
 */
public class PersistenceUnavailableException extends RuntimeException {

    /**
     * Creates a new {@code PersistenceUnavailableException} with the specified detail message.
     *
     * @param message the detail message explaining the cause of the persistence failure
     */
    public PersistenceUnavailableException(String message) {
        super(message);
    }

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