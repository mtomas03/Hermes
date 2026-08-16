package it.unibo.hermes.gateway.exception;

/**
 * Thrown when Kafka is unreachable after all retries.
 */
public class BackboneUnavailableException extends HermesException {
    public BackboneUnavailableException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
