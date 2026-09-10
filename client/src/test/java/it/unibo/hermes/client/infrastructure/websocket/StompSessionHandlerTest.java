package it.unibo.hermes.client.infrastructure.websocket;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.AckDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StompSessionHandlerTest {

    @Mock
    private StompSession session;
    @Mock
    private StompHeaders headers;

    private List<InboundMessageDto> receivedMessages;
    private List<AckDto> receivedAcks;
    private boolean connectedCalled;
    private Throwable observedError;
    private boolean disconnectedCalled;

    private StompSessionHandler handler;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        ReflectionTestUtils.setField(props, "stompMessagesDestination", "/user/queue/messages");
        ReflectionTestUtils.setField(props, "stompAcksDestination", "/user/queue/acks");

        receivedMessages = new ArrayList<>();
        receivedAcks = new ArrayList<>();

        Consumer<InboundMessageDto> onMessage = receivedMessages::add;
        Consumer<AckDto> onAck = receivedAcks::add;
        Runnable onConnected = () -> connectedCalled = true;
        Consumer<Throwable> onError = e -> observedError = e;
        Runnable onDisconnected = () -> disconnectedCalled = true;

        handler = new StompSessionHandler(props, onMessage, onAck, onConnected, onError, onDisconnected);
    }

    @Test
    void shouldSubscribeToMessagesAndAcksDestinationsOnConnect() {
        handler.afterConnected(session, headers);

        verify(session).subscribe(eq("/user/queue/messages"), any(StompFrameHandler.class));
        verify(session).subscribe(eq("/user/queue/acks"), any(StompFrameHandler.class));
    }

    @Test
    void shouldInvokeOnConnectedCallbackAfterSubscribing() {
        handler.afterConnected(session, headers);

        assertTrue(connectedCalled);
    }

    @Test
    void shouldDispatchInboundMessageFrameToOnMessageCallback() {
        handler.afterConnected(session, headers);
        ArgumentCaptor<StompFrameHandler> captor = ArgumentCaptor.forClass(StompFrameHandler.class);
        verify(session).subscribe(eq("/user/queue/messages"), captor.capture());
        StompFrameHandler messageFrameHandler = captor.getValue();
        InboundMessageDto dto = new InboundMessageDto(
                "m1", "alice-bob", "bob", "alice", "hi", 1L, Instant.now(), "SENT");

        messageFrameHandler.handleFrame(headers, dto);

        assertEquals(1, receivedMessages.size());
        assertEquals("m1", receivedMessages.getFirst().messageId());
    }

    @Test
    void shouldDispatchAckFrameToOnAckCallback() {
        handler.afterConnected(session, headers);
        ArgumentCaptor<StompFrameHandler> captor = ArgumentCaptor.forClass(StompFrameHandler.class);
        verify(session).subscribe(eq("/user/queue/acks"), captor.capture());
        StompFrameHandler ackFrameHandler = captor.getValue();
        AckDto ack = new AckDto("m1", "DELIVERED");

        ackFrameHandler.handleFrame(headers, ack);

        assertEquals(1, receivedAcks.size());
        assertEquals("m1", receivedAcks.getFirst().messageId());
    }

    @Test
    void shouldIgnoreFrameWithUnexpectedPayloadType() {
        handler.afterConnected(session, headers);
        ArgumentCaptor<StompFrameHandler> captor = ArgumentCaptor.forClass(StompFrameHandler.class);
        verify(session).subscribe(eq("/user/queue/messages"), captor.capture());
        StompFrameHandler messageFrameHandler = captor.getValue();

        // Payload of the wrong type must simply be ignored, not throw
        messageFrameHandler.handleFrame(headers, "not-a-dto");

        assertTrue(receivedMessages.isEmpty());
    }

    @Test
    void shouldForwardTransportExceptionsToErrorCallback() {
        RuntimeException boom = new RuntimeException("boom");

        handler.handleException(session, null, headers, new byte[0], boom);

        assertEquals(boom, observedError);
    }

    @Test
    void shouldNotifyDisconnectionAndErrorOnTransportError() {
        RuntimeException boom = new RuntimeException("connection reset");

        handler.handleTransportError(session, boom);

        assertTrue(disconnectedCalled);
        assertEquals(boom, observedError);
    }
}
