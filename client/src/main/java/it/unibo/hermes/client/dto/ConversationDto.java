package it.unibo.hermes.client.dto;

public record ConversationDto(
        String conversationId,
        String myUsername,
        String participantUsername) {
}
