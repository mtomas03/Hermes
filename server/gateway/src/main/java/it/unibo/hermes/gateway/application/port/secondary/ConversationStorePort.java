package it.unibo.hermes.gateway.application.port.secondary;

import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.model.Conversation;

import java.util.List;

/**
 * Secondary port interface for conversation membership lookup.
 */
public interface ConversationStorePort {

    /**
     * Finds all conversations that a user participates in.
     *
     * @param username the username of the participant
     * @return the conversations the participant belongs to
     */
    List<Conversation> findByUsername(String username);

    /**
     * Checks if a user participates in a specific conversation.
     *
     * @param username       the username of the user
     * @param conversationId the identifier of the conversation
     * @return true if the user participates in the conversation
     * @throws PersistenceUnavailableException if the message backbone and the message database are unreachable
     */
    boolean isParticipant(String username, String conversationId);
}
