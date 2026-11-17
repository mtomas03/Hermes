package it.unibo.hermes.worker.application.exception;

/**
 * Thrown when the worker fails to publish a delivery event to the message broker.
 */
public class DeliveryPublicationException extends RuntimeException {

    public DeliveryPublicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
