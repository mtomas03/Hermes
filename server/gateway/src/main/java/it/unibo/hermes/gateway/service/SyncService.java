package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.CassandraMessageAdapter;
import it.unibo.hermes.gateway.domain.Message;
import it.unibo.hermes.gateway.dto.SyncResponse;
import it.unibo.hermes.gateway.dto.SyncResponse.MessageDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service executing pull-based message synchronisation requests between conversational participants.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final CassandraMessageAdapter cassandraAdapter;

    /**
     * Creates the synchronisation service.
     *
     * @param cassandraAdapter the adapter managing message persistence queries in Cassandra
     */
    public SyncService(CassandraMessageAdapter cassandraAdapter) {
        this.cassandraAdapter = cassandraAdapter;
    }

    /**
     * Retrieves all missing messages within a conversation occurring after the specified logical timestamp.
     *
     * @param requestingUser the authenticated username requesting synchronisation
     * @param conversationId the conversation identifier
     * @param afterLogicalTs the last known logical timestamp cursor held by the client, or -1 to fetch complete history
     * @return a synchronisation response containing missing message data transfer objects
     * @throws AccessDeniedException if the requesting user is not a participant in the specified conversation
     */
    public SyncResponse syncMissing(String requestingUser,
                                    String conversationId,
                                    long afterLogicalTs) {

        validateParticipant(requestingUser, conversationId);

        List<Message> messages = afterLogicalTs < 0
                ? cassandraAdapter.findAll(conversationId)
                : cassandraAdapter.findAfter(conversationId, afterLogicalTs);

        List<MessageDto> dtos = messages.stream()
                .map(m -> new MessageDto(
                        m.getMessageId().toString(),
                        m.getConversationId(),
                        m.getSenderUsername(),
                        m.getRecipientUsername(),
                        m.getContent(),
                        m.getLogicalTimestamp(),
                        m.getPhysicalTimestamp(),
                        m.getStatus().name()
                ))
                .toList();

        log.debug("Sync for user '{}' in '{}': {} messages returned (after ts={})",
                requestingUser, conversationId, dtos.size(), afterLogicalTs);

        return new SyncResponse(conversationId, dtos);
    }

    /**
     * Verifies that the requesting user is an authorised participant in the conversation identifier.
     *
     * @param username       the username to validate
     * @param conversationId the conversation identifier
     * @throws AccessDeniedException if the user is not one of the conversation participants
     */
    private void validateParticipant(String username, String conversationId) {
        String[] parts = conversationId.split("-", 2);
        if (parts.length != 2 || (!parts[0].equals(username) && !parts[1].equals(username))) {
            throw new AccessDeniedException(
                    "User '" + username + "' is not a participant of conversation '" + conversationId + "'");
        }
    }
}