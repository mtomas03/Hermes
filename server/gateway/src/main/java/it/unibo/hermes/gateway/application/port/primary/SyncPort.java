package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.SyncResponseDto;

/**
 * Port interface for synchronising conversations.
 */
public interface SyncPort {

    /**
     * Synchronises the conversation with the given ID for the requesting user.
     *
     * @param requestingUser the username of the user requesting the synchronisation
     * @param conversationId the ID of the conversation to synchronise
     * @return a SyncResponseDto containing the full message history of the conversation
     */
    SyncResponseDto syncConversation(String requestingUser,
                                     String conversationId);
}
