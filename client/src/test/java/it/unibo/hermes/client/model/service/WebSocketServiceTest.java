package it.unibo.hermes.client.model.service;

import it.unibo.hermes.client.infrastructure.config.AppProperties;
import it.unibo.hermes.client.model.dto.DeliveryAckDto;
import it.unibo.hermes.client.model.dto.OutboundMessageDto;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketServiceTest {

    private static final String ACK_DESTINATION = "/app/chat.ack";
    private static final String MESSAGE_DESTINATION = "/app/chat.sendMessage";

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
        ReflectionTestUtils.setField(props, "stompSendDestination", MESSAGE_DESTINATION);
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

        verify(stompSession, times(1))
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

    @Test
    void readyToSendCallbackRunsOnlyOnceTheSessionCanBeUsedToSend() {
        when(stompSession.isConnected()).thenReturn(true);
        AtomicBoolean sentFromCallback = new AtomicBoolean();
        service.setOnReadyToSend(() -> sentFromCallback.set(service.sendMessage(createOutboundMessage())));

        connectWith(stompSession);

        // the session is already registered when the callback runs, so a flush is performed and the message is sent
        assertTrue(sentFromCallback.get());
        verify(stompSession).send(eq(MESSAGE_DESTINATION), eq(createOutboundMessage()));
    }

    @Test
    void sendMessageWithoutASessionReportsFailureWithoutThrowing() {
        assertFalse(service.sendMessage(createOutboundMessage()));

        verifyNoInteractions(stompSession);
    }

    @Test
    void sendMessageOnASessionThatClosedMeanwhileReportsFailureInsteadOfThrowing() {
        when(stompSession.isConnected()).thenReturn(true);
        connectWith(stompSession);
        when(stompSession.send(eq(MESSAGE_DESTINATION), any(OutboundMessageDto.class)))
                .thenThrow(new IllegalStateException("Connection closed"));

        assertFalse(service.sendMessage(createOutboundMessage()));
    }

    private OutboundMessageDto createOutboundMessage() {
        return new OutboundMessageDto(
                "m1", "alice-bob",
                "alice", "bob",
                "hi " + "m1", 1L);
    }

    private void connectWith(StompSession session) {
        when(stompClient.connectAsync(anyString(), any(WebSocketHttpHeaders.class), any(StompHeaders.class),
                any(StompSessionHandler.class))).thenReturn(CompletableFuture.completedFuture(session));
        service.connect("token");
    }
}
