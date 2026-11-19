package it.unibo.hermes.gateway.domain.exception;

/**
 * Thrown when a message violates the expected format or contains invalid data.
 */
public class InvalidMessageException extends RuntimeException {

    private final String code;

    public InvalidMessageException(String code, String reason) {
        super(reason);
        this.code = code;
    }

    /**
     * Returns the error code associated with this exception.
     *
     * @return the error code
     */
    public String code() {
        return code;
    }
}
