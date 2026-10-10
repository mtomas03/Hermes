package it.unibo.hermes.client.service;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.ConversationDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
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
    private static final String ACKNOWLEDGED = "ACKNOWLEDGED";

    private final WebClient webClient;
    private final AppProperties props;
    private final LocalPersistenceService persistence;
    private final MessageService messageService;
    private final WebSocketService webSocketService;

    public SyncService(WebClient webClient,
                       AppProperties props,
                       LocalPersistenceService persistence,
                       MessageService messageService,
                       WebSocketService webSocketService) {
        this.webClient = webClient;
        this.props = props;
        this.persistence = persistence;
        this.messageService = messageService;
        this.webSocketService = webSocketService;
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
     * Applies a full-sync response to local persistence and confirms receipt to the server.
     *
     * @param response      the sync response to apply
     * @param localUsername the user running this client, or {@code null} if unknown (no ACK is then sent)
     */
    public void applySync(SyncResponseDto response, String localUsername) {
        for (var dto : response.messages()) {
            Message msg = new Message(
                    dto.messageId(),
                    dto.conversationId(),
                    dto.senderUsername(),
                    dto.recipientUsername(),
                    dto.content(),
                    dto.logicalTimestamp() != null ? dto.logicalTimestamp() : 0L,
                    MessageStatus.SENT);

            boolean persisted = persistence.saveMessage(msg);

            if (dto.logicalTimestamp() != null) {
                messageService.syncConversationClock(
                        response.conversationId(),
                        dto.logicalTimestamp());
            }

            if (!persisted) {
                log.error("Could not persist synced message {} - it will not be acknowledged", dto.messageId());
                continue;
            }
            if (needsAcknowledgement(dto, localUsername)) {
                webSocketService.sendAck(dto.messageId());
            }
        }
    }

    private static boolean needsAcknowledgement(InboundMessageDto dto, String localUsername) {
        return localUsername != null
                && localUsername.equalsIgnoreCase(dto.recipientUsername())
                && !ACKNOWLEDGED.equals(dto.messageStatus());
    }
}
