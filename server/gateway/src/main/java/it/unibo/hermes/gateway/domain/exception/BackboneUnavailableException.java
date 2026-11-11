package it.unibo.hermes.gateway.domain.exception;

/**
 * Thrown when Kafka is unreachable after all retries.
 */
public class BackboneUnavailableException extends RuntimeException {
    public BackboneUnavailableException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
