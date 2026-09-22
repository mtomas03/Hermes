package it.unibo.hermes.gateway.websocket;

import it.unibo.hermes.gateway.security.StompPrincipal;
import it.unibo.hermes.gateway.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PresenceEventListenerTest {

    @Mock
    private WebSocketSessionRegistry registry;
    @Mock
    private PresenceService presenceService;

    private PresenceEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PresenceEventListener(registry, presenceService);
        ReflectionTestUtils.setField(listener, "gatewayInstanceId", "gateway-1");
    }

    private Message<byte[]> authenticatedMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setUser(new StompPrincipal("alice"));
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void shouldRegisterAndMarkOnlineOnSessionConnected() {
        SessionConnectedEvent event = new SessionConnectedEvent(this, authenticatedMessage(),
                new StompPrincipal("alice"));

        listener.onSessionConnected(event);

        verify(registry).register("alice");
        verify(presenceService).setOnline("alice", "gateway-1");
    }

    @Test
    void shouldIgnoreConnectedEventWithNoPrincipal() {
        SessionConnectedEvent event = new SessionConnectedEvent(this, authenticatedMessage());

        listener.onSessionConnected(event);

        verifyNoInteractions(registry, presenceService);
    }

    @Test
    void shouldUnregisterAndMarkOfflineOnSessionDisconnect() {
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, authenticatedMessage(),
                "session-1", org.springframework.web.socket.CloseStatus.NORMAL, new StompPrincipal("alice"));

        listener.onSessionDisconnect(event);

        verify(registry).unregister("alice");
        verify(presenceService).setOffline("alice");
    }

    @Test
    void shouldIgnoreDisconnectEventWithNoPrincipal() {
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, authenticatedMessage(),
                "session-1", org.springframework.web.socket.CloseStatus.NORMAL, null);

        listener.onSessionDisconnect(event);

        verifyNoInteractions(registry, presenceService);
    }
}
