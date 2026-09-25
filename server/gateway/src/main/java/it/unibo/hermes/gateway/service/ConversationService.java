package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.CassandraAdapter;
import it.unibo.hermes.gateway.dto.ConversationDto;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class for managing conversations.
 */
@Service
public class ConversationService {

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
