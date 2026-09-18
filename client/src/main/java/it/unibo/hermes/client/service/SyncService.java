package it.unibo.hermes.client.service;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.ConversationDto;
import it.unibo.hermes.client.dto.SyncResponseDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service responsible for synchronising conversations and messages with the server.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final WebClient webClient;
    private final AppProperties props;
    private final LocalPersistenceService persistence;
    private final MessageService messageService;

    public SyncService(WebClient webClient,
                       AppProperties props,
                       LocalPersistenceService persistence,
                       MessageService messageService) {
        this.webClient = webClient;
        this.props = props;
        this.persistence = persistence;
        this.messageService = messageService;
    }

    /**
     * Fetches all conversations for the authenticated user.
     *
     * @param bearerToken       the JWT bearer token for authentication
     * @return a Mono emitting the list of conversations, or an error if the request fails
     */
    public Mono<List<ConversationDto>> fetchConversations(String bearerToken) {
        return webClient.get()
                .uri(props.getConversationsPath())
                .header("Authorization", bearerToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToFlux(ConversationDto.class)
                .collectList()
                .doOnSuccess(list -> log.info("Fetched {} conversations", list.size()))
                .doOnError(e -> log.warn("Failed to fetch conversations: {}", e.getMessage()));
    }

    /**
     * Fetches the complete history of one conversation.
     *
     * @param conversationId    the unique identifier of the conversation to synchronise
     * @param bearerToken       the JWT bearer token for authentication
     * @return a Mono emitting the synchronisation response containing messages,
     *         or an error if the request fails
     */
    public Mono<SyncResponseDto> syncConversation(String conversationId, String bearerToken) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(props.getSyncPath())
                        .pathSegment(conversationId)
                        .build())
                .header("Authorization", bearerToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(SyncResponseDto.class)
                .doOnSuccess(response -> log.info("Full sync for {} - {} messages",
                        conversationId, response.messages().size()))
                .doOnError(e -> log.warn("Sync error for {}: {}", conversationId, e.getMessage()));
    }

    /**
     * Merges the synchronisation response with the local database,
     * saving any new messages and updating the Lamport clock.
     *
     * @param response      the synchronisation response containing messages to merge
     */
    public void applySync(SyncResponseDto response) {
        for (var dto : response.messages()) {
            Message msg = new Message(
                    dto.messageId(),
                    dto.conversationId(),
                    dto.senderUsername(),
                    dto.recipientUsername(),
                    dto.content(),
                    dto.logicalTimestamp() != null ? dto.logicalTimestamp() : 0L,
                    MessageStatus.SENT);

            persistence.saveMessage(msg);

            if (dto.logicalTimestamp() != null) {
                messageService.syncConversationClock(
                        response.conversationId(),
                        dto.logicalTimestamp());
            }
        }
    }
}
