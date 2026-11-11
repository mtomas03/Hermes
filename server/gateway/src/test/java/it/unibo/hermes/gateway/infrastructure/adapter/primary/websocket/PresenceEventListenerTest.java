package it.unibo.hermes.gateway.infrastructure.adapter.primary.websocket;

import it.unibo.hermes.gateway.application.port.primary.SessionLifecyclePort;
import it.unibo.hermes.gateway.infrastructure.security.StompPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.web.socket.CloseStatus.NORMAL;

@ExtendWith(MockitoExtension.class)
class PresenceEventListenerTest {

    @Mock
    private SessionLifecyclePort sessionLifecycle;

    private PresenceEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PresenceEventListener(sessionLifecycle);
    }

    private Message<byte[]> authenticatedMessage() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setUser(new StompPrincipal("alice"));
        accessor.setSessionId("session-1");
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    void shouldReportClientConnectedOnSessionConnected() {
        SessionConnectedEvent event = new SessionConnectedEvent(this, authenticatedMessage(),
                new StompPrincipal("alice"));

        listener.onSessionConnected(event);

        verify(sessionLifecycle).clientConnected("alice");
    }

    @Test
    void shouldIgnoreConnectedEventWithNoPrincipal() {
        SessionConnectedEvent event = new SessionConnectedEvent(this, authenticatedMessage());

        listener.onSessionConnected(event);

        verifyNoInteractions(sessionLifecycle);
    }

    @Test
    void shouldReportClientDisconnectedOnSessionDisconnect() {
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, authenticatedMessage(),
                "session-1", NORMAL, new StompPrincipal("alice"));

        listener.onSessionDisconnect(event);

        verify(sessionLifecycle).clientDisconnected("alice");
    }

    @Test
    void shouldIgnoreDisconnectEventWithNoPrincipal() {
        SessionDisconnectEvent event = new SessionDisconnectEvent(this, authenticatedMessage(),
                "session-1", NORMAL, null);

        listener.onSessionDisconnect(event);

        verifyNoInteractions(sessionLifecycle);
    }
}
