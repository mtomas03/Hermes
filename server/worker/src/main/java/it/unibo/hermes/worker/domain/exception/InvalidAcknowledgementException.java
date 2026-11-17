package it.unibo.hermes.worker.domain.exception;

import it.unibo.hermes.worker.domain.model.Acknowledgement;

/**
 * Thrown when the data used to build an {@link Acknowledgement}
 * violates a Domain rule.
 */
public class InvalidAcknowledgementException extends DomainException {

    public InvalidAcknowledgementException(String message) {
        super(message);
    }
}
