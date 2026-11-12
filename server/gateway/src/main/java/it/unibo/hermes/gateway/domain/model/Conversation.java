package it.unibo.hermes.gateway.domain.model;

/**
 * A conversation from the point of view of one of its participants.
 *
 * @param conversationId   the conversation identifier
 * @param owner            the participant this view belongs to
 * @param otherParticipant the other participant
 */
public record Conversation(
        String conversationId,
        String owner,
        String otherParticipant
) {
}
