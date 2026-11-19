package it.unibo.hermes.gateway.domain.exception;

/**
 * Thrown when a username does not respect the canonical username format.
 */
public class InvalidUsernameException extends IllegalArgumentException {

    public InvalidUsernameException(String message) {
        super(message);
    }
}