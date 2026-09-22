package it.unibo.hermes.gateway.dto;

/**
 * Data Transfer Object representing the message sent by a client
 * to the Gateway over STOMP to {@code /app/chat.sendMessage}.
 *
 * <p> The {@code senderUsername} field sent by the client is never trusted: the authoritative sender
 * is always the authenticated STOMP {@link java.security.Principal} associated with the session.
 *
 * @param messageId         the unique identifier of the message
 * @param conversationId    the unique identifier of the conversation to which this message belongs
 * @param senderUsername    the username the client claims as sender (not authoritative)
 * @param recipientUsername the username of the recipient of the message
 * @param content           the content of the message
 * @param logicalTimestamp  the Lamport logical timestamp of this message
 */
public record MessageToGatewayDto(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp
) {}
