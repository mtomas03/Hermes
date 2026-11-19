package it.unibo.hermes.worker.domain.exception;

/**
 * Base type of exceptions that are caused by the data
 * being processed violating a Domain rule.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
