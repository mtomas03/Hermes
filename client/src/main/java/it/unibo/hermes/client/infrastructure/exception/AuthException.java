package it.unibo.hermes.client.infrastructure.exception;

/**
 * Thrown when an authentication operation fails.
 */
public class AuthException extends RuntimeException {

    private final Reason reason;
    private final int statusCode;

    public AuthException(Reason reason, int statusCode, String message) {
        this(reason, statusCode, message, null);
    }

    public AuthException(Reason reason, int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
        this.statusCode = statusCode;
    }

    public Reason getReason() {
        return reason;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public enum Reason {
        INVALID_CREDENTIALS,
        USERNAME_TAKEN,
        BAD_REQUEST,
        SERVICE_UNAVAILABLE,
        SERVER_ERROR,
        NETWORK_ERROR
    }
}