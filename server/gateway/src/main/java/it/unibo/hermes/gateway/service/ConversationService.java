package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.dto.ConversationDto;
import it.unibo.hermes.gateway.repository.cassandra.ConversationByUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ConversationService {

    private final ConversationByUserRepository conversationRepository;

    public ConversationService(ConversationByUserRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public List<ConversationDto> getUserConversations(String currentUsername) {
        return conversationRepository.findByUsername(currentUsername)
                .stream()
                .map(conv -> new ConversationDto(
                        conv.getConversationId(),
                        currentUsername,
                        conv.getOtherParticipant()
                ))
                .toList();
    }
}
