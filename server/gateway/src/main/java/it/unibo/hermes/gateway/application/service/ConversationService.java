package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.primary.ConversationPort;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.ConversationDto;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.CassandraAdapter;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class for managing conversations.
 */
@Service
public class ConversationService implements ConversationPort {

    private final CassandraAdapter cassandraAdapter;

    public ConversationService(CassandraAdapter cassandraAdapter) {
        this.cassandraAdapter = cassandraAdapter;
    }

    /**
     * Retrieves a list of conversations for the specified user.
     *
     * @param currentUsername the username of the current user
     * @return a list of ConversationDto representing the user's conversations
     */
    @Override
    public List<ConversationDto> getUserConversations(String currentUsername) {
        return cassandraAdapter.findConversationsByUsername(currentUsername)
                .stream()
                .map(conv -> new ConversationDto(
                        conv.getConversationId(),
                        currentUsername,
                        conv.getOtherParticipant()
                ))
                .toList();
    }
}
