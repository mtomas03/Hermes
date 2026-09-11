package it.unibo.hermes.client.dto;

/**
 * Message received from the server via STOMP or REST.
 */
public record InboundMessageDto(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp,
        String status) {
}
