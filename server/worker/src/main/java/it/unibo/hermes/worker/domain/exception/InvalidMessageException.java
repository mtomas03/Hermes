package it.unibo.hermes.worker.domain.exception;

import it.unibo.hermes.worker.domain.model.Message;

/**
 * Thrown when the data used to build a {@link Message} violates a Domain rule.
 */
public class InvalidMessageException extends DomainException {

    public InvalidMessageException(String message) {
        super(message);
    }
}
