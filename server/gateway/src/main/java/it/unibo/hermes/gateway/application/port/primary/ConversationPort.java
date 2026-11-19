package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.domain.model.Conversation;

import java.util.List;

/**
 * Primary port interface for managing conversations.
 */
public interface ConversationPort {

    /**
     * Retrieves the conversations of the specified user.
     *
     * @param currentUsername the username of the current user
     * @return the user's conversations
     */
    List<Conversation> getUserConversations(String currentUsername);
}
