package it.unibo.hermes.client.dto;

import java.time.Instant;

public record ConversationDto(
        String conversationId,
        String myUsername,
        String participantUsername,
        String lastMessageId,
        Instant lastMessageTimestamp) {
}
