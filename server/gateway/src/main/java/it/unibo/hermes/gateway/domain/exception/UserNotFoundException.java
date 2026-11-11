package it.unibo.hermes.gateway.domain.exception;

/**
 * Thrown when a requested user does not exist.
 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
