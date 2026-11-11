package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.primary.SyncPort;
import it.unibo.hermes.gateway.application.port.secondary.ConversationStorePort;
import it.unibo.hermes.gateway.application.port.secondary.MessageStorePort;
import it.unibo.hermes.gateway.domain.exception.ConversationAccessDeniedException;
import it.unibo.hermes.gateway.domain.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service executing full conversation synchronisation requests.
 */
@Service
public class SyncService implements SyncPort {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final ConversationStorePort conversationStore;
    private final MessageStorePort messageStore;

    /**
     * Creates the synchronisation service.
     *
     * @param conversationStore the port providing conversation membership
     * @param messageStore      the port providing message history
     */
    public SyncService(ConversationStorePort conversationStore, MessageStorePort messageStore) {
        this.conversationStore = conversationStore;
        this.messageStore = messageStore;
    }

    @Override
    public List<Message> syncConversation(String requestingUser, String conversationId) {
        validateParticipant(requestingUser, conversationId);

        List<Message> messages = messageStore.findAllByConversation(conversationId);

        log.debug("Full sync for user '{}' in '{}': {} messages returned",
                requestingUser, conversationId, messages.size());

        return messages;
    }

    private void validateParticipant(String username, String conversationId) {
        if (!conversationStore.isParticipant(username, conversationId)) {
            throw new ConversationAccessDeniedException(
                    "User '" + username + "' is not a participant of conversation '" + conversationId + "'");
        }
    }
}
