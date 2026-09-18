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
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Controller responsible for managing the synchronisation of conversations
 * and messages between the client and the server.
 */
@Component
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final SyncService syncService;
    private final LocalPersistenceService persistence;
    private final ClientStateModel stateModel;
    private final ConcurrentMap<String, Mono<SyncResult>> syncsInProgress =
            new ConcurrentHashMap<>();
    private final Set<String> syncedConversations = ConcurrentHashMap.newKeySet();

    public SyncController(SyncService syncService,
                          LocalPersistenceService persistence,
                          ClientStateModel stateModel) {
        this.syncService = syncService;
        this.persistence = persistence;
        this.stateModel = stateModel;
    }

    /**
     * Initiates a full synchronisation of all conversations and their messages.
     */
    public void syncAll() {
        AuthToken token = stateModel.getAuthToken();
        if (token == null || !token.isValid()) {
            log.warn("Skipping sync - no valid auth token");
            return;
        }

        log.info("Starting full synchronisation");
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
     * Synchronises a specific conversation if it has not already been synchronised
     * in the current session. If the conversation is already synced, this method
     * will not perform any action.
     *
     * @param conversationId    the unique identifier of the conversation to synchronise
     */
    public void syncConversationIfNeeded(String conversationId) {
        AuthToken token = stateModel.getAuthToken();
        if (token == null || !token.isValid()) {
            log.warn("Skipping conversation sync - no valid auth token");
            return;
        }

        if (syncedConversations.contains(conversationId)) {
            log.debug("Conversation {} already synced in this session", conversationId);
            return;
        }

        startConversationSync(conversationId, token)
                .subscribe(
                        result -> {
                            Conversation selected = stateModel.getSelectedConversation();
                            if (selected != null && conversationId.equals(selected.conversationId())) {
                                refreshActiveMessages(conversationId);
                            }
                        },
                        err -> log.warn("Conversation sync failed for {}: {}", conversationId, err.getMessage()));
    }

    /**
     * Refreshes the active messages for a given conversation by reloading them from
     * the local persistence layer and updating the client state model.
     *
     * @param conversationId    the unique identifier of the conversation whose messages are to be refreshed
     */
    public void refreshActiveMessages(String conversationId) {
        List<Message> messages = persistence.loadMessages(conversationId);
        stateModel.replaceMessages(messages);
    }

    /**
     * Resets the synchronisation state, clearing the set of synced conversations
     * and the in-progress sync operations.
     */
    public void resetSyncState() {
        syncedConversations.clear();
        syncsInProgress.clear();
    }

    private void processConversations(List<ConversationDto> conversationDtos, AuthToken token) {
        for (ConversationDto dto : conversationDtos) {
            User user = new User(dto.otherParticipantUsername());
            Conversation conversation = new Conversation(dto.conversationId(), user);
            persistence.saveConversation(conversation);
        }

        stateModel.setConversations(persistence.loadAllConversations());

        Conversation selected = stateModel.getSelectedConversation();
        String selectedId = selected != null ? selected.conversationId() : null;

        Flux<ConversationDto> ordered = Flux.fromIterable(conversationDtos);

        if (selectedId != null) {
            ordered = Flux.concat(
                    Flux.fromIterable(conversationDtos)
                            .filter(dto -> selectedId.equals(dto.conversationId()))
                            .take(1),
                    Flux.fromIterable(conversationDtos)
                            .filter(dto -> !selectedId.equals(dto.conversationId()))
            );
        }

        ordered
                .flatMap(dto -> startConversationSync(dto.conversationId(), token)
                                .onErrorResume(err -> Mono.empty()),
                        4)
                .subscribe(
                        result -> {
                            if (selectedId != null && selectedId.equals(result.conversationId())) {
                                refreshActiveMessages(result.conversationId());
                            }
                        },
                        err -> log.warn("Partial sync error: {}", err.getMessage()),
                        () -> {
                            log.info("Conversation synchronization complete");
                            stateModel.setSyncing(false);
                            stateModel.setStatusMessage("Online");
                        });
    }

    private Mono<SyncResult> startConversationSync(String conversationId, AuthToken token) {
        return syncsInProgress.computeIfAbsent(
                conversationId,
                id -> syncService
                        .syncConversation(id, token.bearerHeader())
                        .map(response -> {
                            syncService.applySync(response);
                            return new SyncResult(id, response.messages().size());
                        })
                        .doOnSuccess(result -> {
                            syncedConversations.add(id);
                            log.info("Conversation {} synchronized: {} messages", result.conversationId(), result.messageCount());
                        })
                        .doOnError(err -> log.warn("Sync failed for {}: {}", id, err.getMessage()))
                        .doFinally(signal -> syncsInProgress.remove(id))
                        .cache()
        );
    }

    private record SyncResult(String conversationId, int messageCount) {}
}
