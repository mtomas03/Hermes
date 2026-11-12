package it.unibo.hermes.gateway.application.port.secondary;

import it.unibo.hermes.gateway.domain.exception.InvalidMessageException;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.model.Message;

import java.util.List;

/**
 * Secondary port interface for direct message persistence, used for the history read
 * and for the message database write, when the message backbone is unavailable.
 */
public interface MessageStorePort {

    /**
     * Persists a message directly, bypassing the message backbone.
     *
     * @param message the message to persist
     * @throws InvalidMessageException if the id is already used by a different message
     */
    void saveFallback(Message message);

    /**
     * @param conversationId the conversation
     * @return all messages of the conversation
     * @throws PersistenceUnavailableException if the message backbone and database are unreachable
     */
    List<Message> findAllByConversation(String conversationId);
}
