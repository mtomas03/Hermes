package it.unibo.hermes.client.dto;

import java.time.Instant;

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
        Instant physicalTimestamp,
        String status) {
}
