package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.dto.ConversationDto;
import it.unibo.hermes.client.dto.SyncResponseDto;
import it.unibo.hermes.client.model.domain.*;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.service.LocalPersistenceService;
import it.unibo.hermes.client.service.SyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SyncControllerTest {

    private static final long ASYNC_TIMEOUT_MS = 2000;

    @Mock
    private SyncService syncService;
    @Mock
    private LocalPersistenceService persistence;
    @Mock
    private ClientStateModel stateModel;

    private SyncController controller;

    @BeforeEach
    void setUp() {
        controller = new SyncController(syncService, persistence, stateModel);
    }

    @Test
    void syncAllShouldDoNothingWithoutValidAuthToken() {
        when(stateModel.getAuthToken()).thenReturn(null);

        controller.syncAll();

        verifyNoInteractions(syncService);
    }

    @Test
    void syncAllShouldFetchAndSyncConversationsWhenTokenIsValid() {
        AuthToken validToken = new AuthToken("valid-jwt", Instant.now().plusSeconds(3600));
        when(stateModel.getAuthToken()).thenReturn(validToken);
        ConversationDto dto = new ConversationDto("alice-bob", "alice", "bob");
        when(syncService.fetchConversations(validToken.bearerHeader()))
                .thenReturn(Mono.just(List.of(dto)));
        when(persistence.loadAllConversations())
                .thenReturn(List.of(new Conversation("alice-bob", new User("bob"))));

        SyncResponseDto syncResponse = new SyncResponseDto("alice-bob", List.of());
        when(syncService.syncConversation(eq("alice-bob"), eq(validToken.bearerHeader())))
                .thenReturn(Mono.just(syncResponse));

        controller.syncAll();

        verify(persistence, timeout(ASYNC_TIMEOUT_MS)).saveConversation(any(Conversation.class));
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setConversations(anyList());
        verify(syncService, timeout(ASYNC_TIMEOUT_MS)).applySync(syncResponse);
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setSyncing(false);
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setStatusMessage("Online");
    }

    @Test
    void syncConversationIfNeededShouldSyncAndRefreshIfSelected() {
        AuthToken validToken = new AuthToken("valid-jwt", Instant.now().plusSeconds(3600));
        Conversation selectedConv = new Conversation("alice-bob", new User("bob"));
        when(stateModel.getAuthToken()).thenReturn(validToken);
        when(stateModel.getSelectedConversation()).thenReturn(selectedConv);
        SyncResponseDto syncResponse = new SyncResponseDto("alice-bob", List.of());
        when(syncService.syncConversation(eq("alice-bob"), eq(validToken.bearerHeader())))
                .thenReturn(Mono.just(syncResponse));

        controller.syncConversationIfNeeded("alice-bob");

        verify(syncService, timeout(ASYNC_TIMEOUT_MS)).applySync(syncResponse);
        verify(persistence, timeout(ASYNC_TIMEOUT_MS)).loadMessages("alice-bob");
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).replaceMessages(anyList());
    }

    @Test
    void syncConversationIfNeededShouldSkipIfAlreadySyncedInCurrentSession() {
        AuthToken validToken = new AuthToken("valid-jwt", Instant.now().plusSeconds(3600));
        when(stateModel.getAuthToken()).thenReturn(validToken);
        SyncResponseDto syncResponse = new SyncResponseDto("alice-bob", List.of());
        when(syncService.syncConversation(eq("alice-bob"), eq(validToken.bearerHeader())))
                .thenReturn(Mono.just(syncResponse));

        controller.syncConversationIfNeeded("alice-bob");
        verify(syncService, timeout(ASYNC_TIMEOUT_MS)).syncConversation(eq("alice-bob"), anyString());
        controller.syncConversationIfNeeded("alice-bob");

        verify(syncService, times(1)).syncConversation(eq("alice-bob"), anyString());
    }

    @Test
    void resetSyncStateShouldAllowReSyncingConversations() {
        AuthToken validToken = new AuthToken("valid-jwt", Instant.now().plusSeconds(3600));
        when(stateModel.getAuthToken()).thenReturn(validToken);
        SyncResponseDto syncResponse = new SyncResponseDto("alice-bob", List.of());
        when(syncService.syncConversation(eq("alice-bob"), eq(validToken.bearerHeader())))
                .thenReturn(Mono.just(syncResponse));

        controller.syncConversationIfNeeded("alice-bob");
        verify(syncService, timeout(ASYNC_TIMEOUT_MS)).syncConversation(eq("alice-bob"), anyString());
        controller.resetSyncState();

        controller.syncConversationIfNeeded("alice-bob");
        verify(syncService, timeout(ASYNC_TIMEOUT_MS).times(2)).syncConversation(eq("alice-bob"), anyString());
    }

    @Test
    void refreshActiveMessagesShouldReloadFromLocalPersistence() {
        Message msg = new Message(
                "m1", "alice-bob", "alice", "bob",
                "hi", 1L, MessageStatus.SENT);
        when(persistence.loadMessages("alice-bob")).thenReturn(List.of(msg));

        controller.refreshActiveMessages("alice-bob");

        verify(stateModel).replaceMessages(List.of(msg));
    }
}
