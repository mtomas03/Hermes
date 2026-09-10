package it.unibo.hermes.client.dto;

import java.time.Instant;

/**
 * Message sent by this client via STOMP.
 */
public record OutboundMessageDto(
        String messageId,
        String conversationId,
        String myUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp,
        Instant physicalTimestamp) {
}
