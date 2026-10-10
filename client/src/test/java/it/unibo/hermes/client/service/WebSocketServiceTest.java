package it.unibo.hermes.client.service;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.DeliveryAckDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketServiceTest {

    private static final String ACK_DESTINATION = "/app/chat.ack";

    @Mock
    private WebSocketStompClient stompClient;
    @Mock
    private StompSession stompSession;

    private WebSocketService service;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        ReflectionTestUtils.setField(props, "wsUrl", "ws://localhost/ws");
        ReflectionTestUtils.setField(props, "stompSendAckDestination", ACK_DESTINATION);
        service = new WebSocketService(stompClient, props);
    }

    @Test
    void ackWhileDisconnectedIsKeptAndSentWhenTheSessionIsEstablished() {
        service.sendAck("m1");
        verifyNoInteractions(stompSession);

        when(stompSession.isConnected()).thenReturn(true);
        connectWith(stompSession);

        verify(stompSession).send(eq(ACK_DESTINATION), eq(new DeliveryAckDto("m1")));
    }

    @Test
    void ackWhileConnectedIsSentImmediatelyAndExactlyOnce() {
        when(stompSession.isConnected()).thenReturn(true);
        connectWith(stompSession);

        service.sendAck("m1");
        service.flushPendingAcks();

        verify(stompSession, times(1))
                .send(eq(ACK_DESTINATION), eq(new DeliveryAckDto("m1")));
    }

    @Test
    void ackWhoseTransmissionFailsStaysPendingAndIsRetriedOnNextFlush() {
        when(stompSession.isConnected()).thenReturn(true);
        connectWith(stompSession);
        when(stompSession.send(eq(ACK_DESTINATION), any(DeliveryAckDto.class)))
                .thenThrow(new IllegalStateException("session closed"))
                .thenReturn(null);

        service.sendAck("m1");
        service.flushPendingAcks();

        verify(stompSession, times(2))
                .send(eq(ACK_DESTINATION), eq(new DeliveryAckDto("m1")));
    }

    @Test
    void pendingAcksAreDroppedWhenTheConnectionIsClosedOnPurpose() {
        service.sendAck("m1");
        service.disconnect();

        when(stompSession.isConnected()).thenReturn(true);
        connectWith(stompSession);

        verify(stompSession, never()).send(anyString(), any());
    }

    private void connectWith(StompSession session) {
        when(stompClient.connectAsync(anyString(), any(WebSocketHttpHeaders.class), any(StompHeaders.class),
                any(StompSessionHandler.class))).thenReturn(CompletableFuture.completedFuture(session));
        service.connect("token");
    }
}
