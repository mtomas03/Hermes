package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.model.domain.AuthToken;
import it.unibo.hermes.client.model.domain.Conversation;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.dto.ConversationDto;
import it.unibo.hermes.client.model.service.LocalPersistenceService;
import it.unibo.hermes.client.model.service.SyncService;
import it.unibo.hermes.client.model.state.ClientStateModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

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
    private final AtomicLong syncGeneration = new AtomicLong(0);
    private final AtomicReference<Disposable.Composite> activeSyncs =
            new AtomicReference<>(Disposables.composite());

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

        long gen = syncGeneration.get();
        User sessionUser = stateModel.getCurrentUser();
        Disposable.Composite sessionDisposables = activeSyncs.get();

        log.info("Starting full synchronisation");
        stateModel.setSyncing(true);
        stateModel.setStatusMessage("Synchronising");

        Disposable sub = syncService.fetchConversations(token.bearerHeader())
                .subscribeOn(Schedulers.boundedElastic())
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        list -> {
                            if (!isSessionActive(gen, sessionUser)) {
                                log.debug("Discarding fetched conversations from stale sync session");
                                return;
                            }
                            processConversations(list, token, gen, sessionUser, sessionDisposables);
                        },
                        err -> {
                            if (!isSessionActive(gen, sessionUser)) {
                                return;
                            }
                            log.error("Failed to fetch conversations", err);
                            stateModel.setSyncing(false);
                            stateModel.setStatusMessage("Sync failed");
                        });
        sessionDisposables.add(sub);
    }

    /**
     * Synchronises a specific conversation if it has not already been synchronised
     * in the current session. If the conversation is already synced, this method
     * will not perform any action.
     *
     * @param conversationId the unique identifier of the conversation to synchronise
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

        long gen = syncGeneration.get();
        User sessionUser = stateModel.getCurrentUser();
        Disposable.Composite sessionDisposables = activeSyncs.get();

        Disposable sub = startConversationSync(conversationId, token, gen, sessionUser)
                .subscribe(
                        result -> {
                            if (!isSessionActive(gen, sessionUser)) {
                                return;
                            }
                            Conversation selected = stateModel.getSelectedConversation();
                            if (selected != null && conversationId.equals(selected.conversationId())) {
                                refreshActiveMessages(conversationId);
                            }
                        },
                        err -> {
                            if (!(err instanceof CancellationException)) {
                                log.warn("Conversation sync failed for {}: {}", conversationId, err.getMessage());
                            }
                        });
        sessionDisposables.add(sub);
    }

    /**
     * Refreshes the active messages for a given conversation by reloading them from
     * the local persistence layer and updating the client state model.
     *
     * @param conversationId the unique identifier of the conversation whose messages are to be refreshed
     */
    public void refreshActiveMessages(String conversationId) {
        long gen = syncGeneration.get();
        Schedulers.boundedElastic().schedule(() -> {
            if (syncGeneration.get() != gen) {
                return;
            }
            List<Message> messages = persistence.loadMessages(conversationId);
            if (syncGeneration.get() != gen) {
                return;
            }
            stateModel.replaceMessages(messages);
        });
    }

    /**
     * Resets the synchronisation state, cancelling all in-flight sync operations
     * and clearing the set of synced conversations.
     */
    public void resetSyncState() {
        syncGeneration.incrementAndGet();
        syncedConversations.clear();
        syncsInProgress.clear();
        activeSyncs.getAndSet(Disposables.composite()).dispose();
    }

    private void processConversations(List<ConversationDto> conversationDtos,
                                      AuthToken token,
                                      long gen,
                                      User sessionUser,
                                      Disposable.Composite sessionDisposables) {
        for (ConversationDto dto : conversationDtos) {
            if (!isSessionActive(gen, sessionUser)) {
                log.debug("Aborting conversation persistence - session logged out or changed");
                return;
            }
            User user = new User(dto.otherParticipantUsername());
            Conversation conversation = new Conversation(dto.conversationId(), user);
            persistence.saveConversation(conversation);
        }

        if (!isSessionActive(gen, sessionUser)) {
            return;
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

        Disposable sub = ordered
                .flatMap(dto -> startConversationSync(dto.conversationId(), token, gen, sessionUser)
                                .onErrorResume(err -> Mono.empty()),
                        4)
                .subscribe(
                        result -> {
                            if (isSessionActive(gen, sessionUser)
                                    && selectedId != null
                                    && selectedId.equals(result.conversationId())) {
                                refreshActiveMessages(result.conversationId());
                            }
                        },
                        err -> {
                            if (isSessionActive(gen, sessionUser)) {
                                log.warn("Partial sync error: {}", err.getMessage());
                            }
                        },
                        () -> {
                            if (isSessionActive(gen, sessionUser)) {
                                log.info("Conversation synchronization complete");
                                stateModel.setSyncing(false);
                                stateModel.setStatusMessage("Online");
                            }
                        });
        sessionDisposables.add(sub);
    }

    private Mono<SyncResult> startConversationSync(String conversationId,
                                                   AuthToken token,
                                                   long gen,
                                                   User sessionUser) {
        return syncsInProgress.computeIfAbsent(conversationId, id -> {
            AtomicReference<Mono<SyncResult>> holder = new AtomicReference<>();
            Mono<SyncResult> mono = syncService
                    .syncConversation(id, token.bearerHeader())
                    .publishOn(Schedulers.boundedElastic())
                    .<SyncResult>handle((response, sink) -> {
                        if (!isSessionActive(gen, sessionUser)) {
                            sink.error(new CancellationException(
                                    "Sync cancelled due to logout or session change"));
                            return;
                        }
                        User current = stateModel.getCurrentUser();
                        syncService.applySync(response, current != null ? current.username() : null);
                        sink.next(new SyncResult(id, response.messages().size()));
                    })
                    .doOnSuccess(result -> {
                        if (isSessionActive(gen, sessionUser)) {
                            syncedConversations.add(id);
                            log.info("Conversation {} synchronized: {} messages",
                                    result.conversationId(), result.messageCount());
                        }
                    })
                    .doOnError(err -> {
                        if (!(err instanceof CancellationException)) {
                            log.warn("Sync failed for {}: {}", id, err.getMessage());
                        }
                    })
                    .doFinally(signal -> syncsInProgress.remove(id, holder.get()))
                    .cache();
            holder.set(mono);
            return mono;
        });
    }

    private boolean isSessionActive(long expectedGen, User expectedUser) {
        return syncGeneration.get() == expectedGen
                && Objects.equals(expectedUser, stateModel.getCurrentUser());
    }

    private record SyncResult(String conversationId, int messageCount) {
    }
}
