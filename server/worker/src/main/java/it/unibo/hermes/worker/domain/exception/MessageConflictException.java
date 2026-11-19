package it.unibo.hermes.worker.domain.exception;

import java.util.List;

/**
 * Thrown when a message is received with an identifier that is already in use
 * by a message with different immutable fields.
 */
public class MessageConflictException extends DomainException {

    private final String messageId;
    private final List<String> conflictingFields;

    public MessageConflictException(String messageId, List<String> conflictingFields) {
        super("Message " + messageId + " already exists with different immutable fields " + conflictingFields);
        this.messageId = messageId;
        this.conflictingFields = List.copyOf(conflictingFields);
    }

    public String messageId() {
        return messageId;
    }

    public List<String> conflictingFields() {
        return conflictingFields;
    }
}
