package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.primary.ConversationPort;
import it.unibo.hermes.gateway.application.port.secondary.ConversationStorePort;
import it.unibo.hermes.gateway.domain.model.Conversation;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service managing the conversations of a user.
 */
@Service
public class ConversationService implements ConversationPort {

    private final ConversationStorePort conversationStore;

    /**
     * Creates the conversation service.
     *
     * @param conversationStore the port providing conversation membership
     */
    public ConversationService(ConversationStorePort conversationStore) {
        this.conversationStore = conversationStore;
    }

    @Override
    public List<Conversation> getUserConversations(String currentUsername) {
        return conversationStore.findByUsername(currentUsername);
    }
}
