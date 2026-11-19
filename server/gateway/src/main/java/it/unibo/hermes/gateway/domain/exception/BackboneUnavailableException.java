package it.unibo.hermes.gateway.domain.exception;

/**
 * Thrown when Kafka is unreachable after all retries.
 */
public class BackboneUnavailableException extends RuntimeException {

    private final boolean outcomeUnknown;

    public BackboneUnavailableException(String msg, Throwable cause) {
        this(msg, cause, false);
    }

    public BackboneUnavailableException(String msg, Throwable cause, boolean outcomeUnknown) {
        super(msg, cause);
        this.outcomeUnknown = outcomeUnknown;
    }

    /**
     * Returns whether the outcome of the operation is unknown.
     *
     * <p> This can happen when a timeout occurs while waiting for a response from the Kafka broker.
     * In such cases, it's not clear whether the message was actually published or not.
     *
     * @return true if the outcome of the operation is unknown, false otherwise
     */
    public boolean isOutcomeUnknown() {
        return this.outcomeUnknown;
    }
}
