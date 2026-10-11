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
        heartbeatService = new HeartbeatService(registry, presenceService, "gateway-1");
    }

    @Test
    void shouldRefreshOrRestorePresenceForEveryConnectedUser() {
        when(registry.allUsernames()).thenReturn(Set.of("alice", "bob"));

        heartbeatService.refreshPresenceTtl();

        verify(presenceService).refreshOrRestore("alice", "gateway-1");
        verify(presenceService).refreshOrRestore("bob", "gateway-1");
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
        doThrow(new RuntimeException("Redis down")).when(presenceService).refreshOrRestore("failing-user", "gateway-1");

        heartbeatService.refreshPresenceTtl();

        verify(presenceService).refreshOrRestore("healthy-user", "gateway-1");
    }
}
