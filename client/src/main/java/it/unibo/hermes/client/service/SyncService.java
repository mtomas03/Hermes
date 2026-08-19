package it.unibo.hermes.client.service;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.ConversationDto;
import it.unibo.hermes.client.dto.SyncResponseDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.client.model.domain.SyncCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

/**
 * Pulls conversation list and missing messages from the backend.
 * Uses cursor-based incremental sync: only messages after the last
 * locally-known message ID are requested.
 */
@Service
public class SyncService {

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final WebClient webClient;
    private final AppProperties props;
    private final LocalPersistenceService persistence;

    public SyncService(WebClient webClient, AppProperties props, LocalPersistenceService persistence) {
        this.webClient = webClient;
        this.props = props;
        this.persistence = persistence;
    }

    /**
     * Fetches all conversations for the authenticated user.
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
     * Performs incremental sync for a single conversation.
     * Passes the last-known message ID so the server returns only newer messages.
     */
    public Mono<SyncResponseDto> syncConversation(String conversationId,
                                                  String afterMessageId,
                                                  String bearerToken) {
        String uri = props.getSyncPath()
                + "?conversationId=" + conversationId
                + (afterMessageId != null ? "&afterMessageId=" + afterMessageId : "");

        return webClient.get()
                .uri(uri)
                .header("Authorization", bearerToken)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(SyncResponseDto.class)
                .doOnSuccess(r -> log.info("Sync conversation {} – {} new messages",
                        conversationId, r.messages().size()))
                .doOnError(e -> log.warn("Sync error for {}: {}", conversationId, e.getMessage()));
    }

    /**
     * Persists synced messages and advances the cursor.
     * Safe to call multiple times (insertIfAbsent prevents duplication).
     */
    public void applySync(SyncResponseDto response) {
        for (var dto : response.messages()) {
            Message msg = new Message(
                    dto.messageId(),
                    dto.conversationId(),
                    dto.senderUsername(),
                    dto.recipientUsername(),
                    dto.content(),
                    dto.logicalTimestamp(),
                    dto.physicalTimestamp(),
                    MessageStatus.SENT);
            persistence.saveMessage(msg);
        }

        if (response.cursorMessageId() != null) {
            SyncCursor cursor = new SyncCursor(
                    response.conversationId(),
                    response.cursorMessageId(),
                    Instant.now());
            persistence.saveCursor(cursor);
        }
    }
}
