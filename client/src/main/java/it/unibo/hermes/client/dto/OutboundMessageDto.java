package it.unibo.hermes.client.dto;

/**
 * Data Transfer Object representing the message sent by the client
 * to the Gateway over STOMP at {@code /app/chat.sendMessage}.
 *
 * @param messageId         the unique identifier of the message
 * @param conversationId    the unique identifier of the conversation to which this message belongs
 * @param senderUsername    the username of the sender of the message
 * @param recipientUsername the username of the recipient of the message
 * @param content           the content of the message
 * @param logicalTimestamp  the Lamport logical timestamp of this message
 */
public record OutboundMessageDto(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp
) {}
