package it.unibo.hermes.gateway.exception;

/**
 * Thrown when Cassandra is unreachable.
 *
 * <p> When both Kafka and Cassandra are unavailable,
 * the gateway rejects the message submission rather than silently losing it.
 */
public class PersistenceUnavailableException extends HermesException {
    public PersistenceUnavailableException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
