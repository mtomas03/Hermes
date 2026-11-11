package it.unibo.hermes.gateway.domain.exception;

/**
 * Thrown when the supplied credentials do not match any account.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
