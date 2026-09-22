package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatServiceTest {

    @Mock
    private WebSocketSessionRegistry registry;
    @Mock
    private PresenceService presenceService;

    private HeartbeatService heartbeatService;

    @BeforeEach
    void setUp() {
        heartbeatService = new HeartbeatService(registry, presenceService);
    }

    @Test
    void shouldRefreshTtlForEveryConnectedUser() {
        when(registry.allUsernames()).thenReturn(Set.of("alice", "bob"));

        heartbeatService.refreshPresenceTtl();

        verify(presenceService).refreshTtl("alice");
        verify(presenceService).refreshTtl("bob");
    }

    @Test
    void shouldDoNothingWhenNoUsersAreConnected() {
        when(registry.allUsernames()).thenReturn(Set.of());

        heartbeatService.refreshPresenceTtl();

        verifyNoInteractions(presenceService);
    }

    @Test
    void shouldContinueRefreshingOtherUsersWhenOneRefreshFails() {
        when(registry.allUsernames()).thenReturn(Set.of("failing-user", "healthy-user"));
        doThrow(new RuntimeException("Redis down")).when(presenceService).refreshTtl("failing-user");

        heartbeatService.refreshPresenceTtl();

        verify(presenceService).refreshTtl("healthy-user");
    }
}
