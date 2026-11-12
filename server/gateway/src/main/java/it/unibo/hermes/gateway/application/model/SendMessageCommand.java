package it.unibo.hermes.gateway.application.model;

/**
 * Use-case input for sending a chat message.
 *
 * @param messageId         the message identifier
 * @param conversationId    the conversation identifier
 * @param senderUsername    the username of the authenticated sender
 * @param recipientUsername the username of the recipient
 * @param content           the body of the message
 * @param logicalTimestamp  the logical timestamp
 */
public record SendMessageCommand(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp
) {
}
