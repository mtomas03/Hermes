package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.domain.exception.ConversationAccessDeniedException;
import it.unibo.hermes.gateway.domain.model.Message;

import java.util.List;

/**
 * Primary port interface for synchronising conversations.
 */
public interface SyncPort {

    /**
     * Returns the full message history of a conversation for one of its participants.
     *
     * @param requestingUser the username of the user requesting the synchronisation
     * @param conversationId the ID of the conversation to synchronise
     * @return the conversation's messages
     * @throws ConversationAccessDeniedException if the user does not participate in the conversation
     */
    List<Message> syncConversation(String requestingUser, String conversationId);
}
