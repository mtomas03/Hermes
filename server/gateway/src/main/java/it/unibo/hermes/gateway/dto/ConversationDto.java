package it.unibo.hermes.gateway.dto;

/**
 * Data transfer object representing a conversation between two users.
 *
 * @param conversationId            the unique identifier of the conversation
 * @param currentUsername           the username of the current user participating in the conversation
 * @param otherParticipantUsername  the username of the other participant in the conversation
 */
public record ConversationDto(
        String conversationId,
        String currentUsername,
        String otherParticipantUsername
) {}
