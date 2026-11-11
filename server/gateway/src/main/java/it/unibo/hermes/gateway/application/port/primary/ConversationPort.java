package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.ConversationDto;

import java.util.List;

/**
 * Port interface for managing conversations.
 */
public interface ConversationPort {

    /**
     * Retrieves a list of conversations for the specified user.
     *
     * @param currentUsername the username of the current user
     * @return a list of ConversationDto representing the user's conversations
     */
    List<ConversationDto> getUserConversations(String currentUsername);
}
