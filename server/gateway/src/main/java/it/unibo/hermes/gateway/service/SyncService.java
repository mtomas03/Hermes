package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.CassandraAdapter;
import it.unibo.hermes.gateway.dto.SyncResponseDto;
import it.unibo.hermes.gateway.dto.SyncResponseDto.MessageDto;
import it.unibo.hermes.gateway.entity.cassandra.MessageByConversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service executing full conversation synchronisation requests.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final CassandraAdapter cassandraAdapter;

    /**
     * Creates the synchronisation service.
     *
     * @param cassandraAdapter the adapter managing message persistence queries in Cassandra
     */
    public SyncService(CassandraAdapter cassandraAdapter) {
        this.cassandraAdapter = cassandraAdapter;
    }

    /**
     * Retrieves the complete message history of a conversation.
     *
     * @param requestingUser    the user requesting the synchronisation
     * @param conversationId    the ID of the conversation to synchronise
     * @return the SyncResponseDto containing the full message history
     */
    public SyncResponseDto syncConversation(String requestingUser,
                                            String conversationId) {

        validateParticipant(requestingUser, conversationId);

        List<MessageByConversation> messages =
                cassandraAdapter.findAllMessages(conversationId);

        List<MessageDto> dtos = messages.stream()
                .map(m -> new MessageDto(
                        m.getMessageId().toString(),
                        m.getConversationId(),
                        m.getSenderUsername(),
                        m.getRecipientUsername(),
                        m.getContent(),
                        m.getLogicalTimestamp(),
                        m.getDeliveryStatus().name()
                ))
                .toList();

        log.debug("Full sync for user '{}' in '{}': {} messages returned",
                requestingUser, conversationId, dtos.size());

        return new SyncResponseDto(conversationId, dtos);
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
