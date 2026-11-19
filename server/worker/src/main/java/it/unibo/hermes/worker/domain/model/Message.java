package it.unibo.hermes.worker.domain.model;

import it.unibo.hermes.worker.domain.exception.InvalidMessageException;
import it.unibo.hermes.worker.domain.exception.MessageConflictException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * A message sent from a sender to a recipient within a conversation.
 *
 * <p> All fields are immutable once the message has been accepted.
 *
 * @param messageId         the identifier of a message
 * @param conversationId    the identifier of a conversation to which this message belongs
 * @param senderUsername    the sender of the message
 * @param recipientUsername the recipient of the message
 * @param content           the content of the message
 * @param logicalTimestamp  the Lamport logical timestamp assigned by the sender
 */
public record Message(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp) {

    public Message {
        if (messageId == null || !isUUID(messageId)) {
            throw new InvalidMessageException("messageId is missing or is not a valid UUID");
        }
        require(messageId, "conversationId", conversationId);
        require(messageId, "senderUsername", senderUsername);
        require(messageId, "recipientUsername", recipientUsername);
        if (content == null) {
            throw new InvalidMessageException("Message " + messageId + ": content is required");
        }
        if (logicalTimestamp == null) {
            throw new InvalidMessageException("Message " + messageId + ": logicalTimestamp is required");
        }
    }

    /**
     * Checks that {@code otherMessage}, received later under the same identifier,
     * is a duplicate and not a different message reusing the same id.
     *
     * @param otherMessage the message received under the same identifier
     * @throws MessageConflictException if any immutable field differs
     */
    public void verifyConsistentWith(Message otherMessage) {
        if (!messageId.equals(otherMessage.messageId)) {
            throw new IllegalArgumentException("Messages with different identifiers cannot be compared");
        }
        List<String> mismatches = new ArrayList<>();
        if (!Objects.equals(conversationId, otherMessage.conversationId)) {
            mismatches.add("conversationId");
        }
        if (!Objects.equals(senderUsername, otherMessage.senderUsername)) {
            mismatches.add("senderUsername");
        }
        if (!Objects.equals(recipientUsername, otherMessage.recipientUsername)) {
            mismatches.add("recipientUsername");
        }
        if (!Objects.equals(content, otherMessage.content)) {
            mismatches.add("content");
        }
        if (!Objects.equals(logicalTimestamp, otherMessage.logicalTimestamp)) {
            mismatches.add("logicalTimestamp");
        }
        if (!mismatches.isEmpty()) {
            throw new MessageConflictException(messageId, mismatches);
        }
    }

    private boolean isUUID(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void require(String messageId, String field, String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidMessageException("Message " + messageId + ": " + field + " is required");
        }
    }
}
