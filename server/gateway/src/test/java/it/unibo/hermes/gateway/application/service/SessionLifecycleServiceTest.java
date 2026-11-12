package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.secondary.ConnectedClientsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionLifecycleServiceTest {

    @Mock
    private ConnectedClientsPort connectedClients;
    @Mock
    private PresenceService presenceService;

    private SessionLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new SessionLifecycleService(connectedClients, presenceService, "gateway-1");
    }

    @Test
    void shouldRegisterClientAndMarkOnlineOnConnect() {
        service.clientConnected("alice", "s1");

        InOrder order = inOrder(connectedClients, presenceService);
        order.verify(connectedClients).register("alice", "s1");
        order.verify(presenceService).setOnline("alice", "gateway-1");
    }

    @Test
    void shouldMarkOfflineWhenTheLastSessionDisconnects() {
        when(connectedClients.unregister("alice", "s1")).thenReturn(true);

        service.clientDisconnected("alice", "s1");

        InOrder order = inOrder(connectedClients, presenceService);
        order.verify(connectedClients).unregister("alice", "s1");
        order.verify(presenceService).setOffline("alice", "gateway-1");
    }

    @Test
    void shouldKeepPresenceWhenAnotherSessionOfTheUserIsStillOpen() {
        when(connectedClients.unregister("alice", "old-session")).thenReturn(false);

        service.clientDisconnected("alice", "old-session");

        verify(connectedClients).unregister("alice", "old-session");
        verifyNoInteractions(presenceService);
    }
}
