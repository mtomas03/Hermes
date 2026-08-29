package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.dto.ConversationDto;
import it.unibo.hermes.client.model.domain.*;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.service.LocalPersistenceService;
import it.unibo.hermes.client.service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * Manages the full synchronisation cycle after login or reconnection:
 * <ol>
 *   <li>Fetch conversation list from the server.
 *   <li>Persist/merge conversations locally.
 *   <li>Expose conversations to the UI via {@link ClientStateModel}.
 *   <li>For each conversation, pull messages newer than the last local cursor.
 *   <li>Persist new messages; update active message list if that conversation is open.
 * </ol>
 */
@Component
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;
    private final LocalPersistenceService persistence;
    private final ClientStateModel stateModel;

    public SyncController(SyncService syncService,
                          LocalPersistenceService persistence,
                          ClientStateModel stateModel) {
        this.syncService = syncService;
        this.persistence = persistence;
        this.stateModel = stateModel;
    }

    /**
     * Entry point called after WebSocket connection is established.
     */
    public void syncAll() {
        AuthToken token = stateModel.getAuthToken();
        if (token == null || token.isValid()) {
            log.warn("Skipping sync - no valid auth token");
            return;
        }
        log.info("Starting full sync");
        stateModel.setSyncing(true);
        stateModel.setStatusMessage("Synchronising...");

        syncService.fetchConversations(token.bearerHeader())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        list -> processConversations(list, token),
                        err -> {
                            log.error("Failed to fetch conversations", err);
                            stateModel.setSyncing(false);
                            stateModel.setStatusMessage("Sync failed");
                        });
    }

    /**
     * Reloads messages for the given conversation from SQLite into the observable list.
     */
    public void refreshActiveMessages(String conversationId) {
        List<Message> messages = persistence.loadMessages(conversationId);
        stateModel.replaceMessages(messages);
    }

    private void processConversations(List<ConversationDto> dtos, AuthToken token) {
        // 1. Persist / merge conversations into SQLite
        for (ConversationDto dto : dtos) {
            User user = new User(dto.participantUsername());
            Conversation conv = new Conversation(dto.conversationId(), user, dto.lastMessageTimestamp());
            persistence.saveConversation(conv);
        }

        // 2. Reload from SQLite and push to UI (thread-safe via setConversations)
        List<Conversation> local = persistence.loadAllConversations();
        stateModel.setConversations(local);
        log.info("Conversations loaded into UI: {}", local.size());

        // 3. Incremental message sync for each conversation
        Flux.fromIterable(dtos)
                .flatMap(dto -> {
                    String afterId = persistence.loadCursor(dto.conversationId())
                            .map(SyncCursor::getLastSyncedMessageId)
                            .orElse(null);
                    return syncService.syncConversation(
                                    dto.conversationId(), afterId, token.bearerHeader())
                            .onErrorResume(e -> {
                                log.warn("Sync skipped for {}: {}", dto.conversationId(), e.getMessage());
                                return reactor.core.publisher.Mono.empty();
                            });
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        response -> {
                            syncService.applySync(response);
                            // If this conversation is currently open, refresh the message list
                            Conversation selected = stateModel.getSelectedConversation();
                            if (selected != null &&
                                    selected.getConversationId().equals(response.conversationId())) {
                                refreshActiveMessages(response.conversationId());
                            }
                        },
                        err -> log.warn("Partial sync error: {}", err.getMessage()),
                        () -> {
                            log.info("Full sync complete");
                            stateModel.setSyncing(false);
                            stateModel.setStatusMessage("Online");
                        });
    }
}
