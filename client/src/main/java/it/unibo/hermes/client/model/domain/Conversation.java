package it.unibo.hermes.client.model.domain;

/**
 * Represents a 1-to-1 conversation between the local user and one other user.
 */
public record Conversation(String conversationId, User recipientUsername) {
}
