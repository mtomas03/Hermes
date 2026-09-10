package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock
    private WebSocketSessionRegistry registry;
    @Mock
    private PresenceService presenceService;
    @Mock
    private WebSocketSession session;

    private HeartbeatService heartbeatService;

    @BeforeEach
    void setUp() {
        heartbeatService = new HeartbeatService(registry, presenceService);
        ReflectionTestUtils.setField(heartbeatService, "heartbeatTimeoutMs", 90_000L);
    }

    @Test
    void shouldCloseUnregisterAndMarkOfflineStaleOpenSessions() throws Exception {
        when(session.isOpen()).thenReturn(true);
        WebSocketSessionRegistry.Entry entry =
                new WebSocketSessionRegistry.Entry("alice", session, Instant.now().minusSeconds(200));
        when(registry.entriesOlderThan(any())).thenReturn(List.of(entry));

        heartbeatService.checkHeartbeats();

        verify(session).close(CloseStatus.SESSION_NOT_RELIABLE);
        verify(registry).unregister("alice");
        verify(presenceService).setOffline("alice");
    }

    @Test
    void shouldSkipCloseButStillCleanUpAlreadyClosedSession() throws Exception {
        when(session.isOpen()).thenReturn(false);
        WebSocketSessionRegistry.Entry entry =
                new WebSocketSessionRegistry.Entry("bob", session, Instant.now().minusSeconds(200));
        when(registry.entriesOlderThan(any())).thenReturn(List.of(entry));

        heartbeatService.checkHeartbeats();

        verify(session, never()).close(any());
        verify(registry).unregister("bob");
        verify(presenceService).setOffline("bob");
    }

    @Test
    void shouldStillCleanUpWhenClosingThrows() throws Exception {
        when(session.isOpen()).thenReturn(true);
        doThrow(new java.io.IOException("boom")).when(session).close(any());
        WebSocketSessionRegistry.Entry entry =
                new WebSocketSessionRegistry.Entry("carol", session, Instant.now().minusSeconds(200));
        when(registry.entriesOlderThan(any())).thenReturn(List.of(entry));

        heartbeatService.checkHeartbeats();

        // Clean-up still happens even though close() failed
        verify(registry).unregister("carol");
        verify(presenceService).setOffline("carol");
    }

    @Test
    void shouldDoNothingWhenNoStaleSessionsExist() {
        when(registry.entriesOlderThan(any())).thenReturn(List.of());

        heartbeatService.checkHeartbeats();

        verifyNoInteractions(presenceService);
    }
}
