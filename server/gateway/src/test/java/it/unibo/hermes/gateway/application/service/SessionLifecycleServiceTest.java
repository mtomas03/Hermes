package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.secondary.ConnectedClientsPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

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
        service.clientConnected("alice");

        InOrder order = inOrder(connectedClients, presenceService);
        order.verify(connectedClients).register("alice");
        order.verify(presenceService).setOnline("alice", "gateway-1");
    }

    @Test
    void shouldUnregisterClientAndMarkOfflineOnDisconnect() {
        service.clientDisconnected("alice");

        InOrder order = inOrder(connectedClients, presenceService);
        order.verify(connectedClients).unregister("alice");
        order.verify(presenceService).setOffline("alice", "gateway-1");
    }
}
