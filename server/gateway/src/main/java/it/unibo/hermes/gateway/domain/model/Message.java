package it.unibo.hermes.gateway.domain.model;

import java.util.UUID;

/**
 * A chat message sent between two users.
 *
 * @param messageId         the unique message identifier
 * @param conversationId    the conversation the message belongs to
 * @param senderUsername    the username of the sender
 * @param recipientUsername the username of the recipient
 * @param content           the body of the message
 * @param logicalTimestamp  the logical timestamp assigned by the client
 * @param status            the message delivery status
 */
public record Message(
        UUID messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp,
        MessageStatus status
) {
}
