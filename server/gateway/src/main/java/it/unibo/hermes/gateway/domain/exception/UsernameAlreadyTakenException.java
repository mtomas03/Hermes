package it.unibo.hermes.gateway.domain.exception;

/**
 * Thrown when registering a username that already exists.
 */
public class UsernameAlreadyTakenException extends IllegalArgumentException {

    public UsernameAlreadyTakenException(String message) {
        super(message);
    }
}
