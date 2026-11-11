package it.unibo.hermes.gateway.domain.exception;

/**
 * Thrown when a user tries to access a conversation they do not participate in.
 */
public class ConversationAccessDeniedException extends RuntimeException {

    public ConversationAccessDeniedException(String message) {
        super(message);
    }
}
