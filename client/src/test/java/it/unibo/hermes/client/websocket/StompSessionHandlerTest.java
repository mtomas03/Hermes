package it.unibo.hermes.client.websocket;

import it.unibo.hermes.client.config.AppProperties;
import it.unibo.hermes.client.dto.AckDto;
import it.unibo.hermes.client.dto.ErrorDto;
import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.dto.SystemMessageDto;
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
    private List<ErrorDto> receivedAppErrors;
    private List<SystemMessageDto> receivedSystemMessages;
    private boolean connectedCalled;
    private Throwable observedError;
    private boolean disconnectedCalled;

    private StompSessionHandler handler;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        ReflectionTestUtils.setField(props, "stompMessagesDestination", "/user/queue/messages");
        ReflectionTestUtils.setField(props, "stompAcksDestination", "/user/queue/acks");
        ReflectionTestUtils.setField(props, "stompErrorsDestination", "/user/queue/errors");
        ReflectionTestUtils.setField(props, "stompSystemDestination", "/user/queue/system");

        receivedMessages = new ArrayList<>();
        receivedAcks = new ArrayList<>();
        receivedAppErrors = new ArrayList<>();
        receivedSystemMessages = new ArrayList<>();

        Consumer<InboundMessageDto> onMessage = receivedMessages::add;
        Consumer<AckDto> onAck = receivedAcks::add;
        Consumer<ErrorDto> onAppError = receivedAppErrors::add;
        Consumer<SystemMessageDto> onSystemMessage = receivedSystemMessages::add;
        Runnable onConnected = () -> connectedCalled = true;
        Consumer<Throwable> onError = e -> observedError = e;
        Runnable onDisconnected = () -> disconnectedCalled = true;

        handler = new StompSessionHandler(props, onMessage, onAck, onAppError, onSystemMessage,
                onConnected, onError, onDisconnected);
    }

    @Test
    void shouldSubscribeToAllFourDestinationsOnConnect() {
        handler.afterConnected(session, headers);

        verify(session).subscribe(eq("/user/queue/messages"), any(StompFrameHandler.class));
        verify(session).subscribe(eq("/user/queue/acks"), any(StompFrameHandler.class));
        verify(session).subscribe(eq("/user/queue/errors"), any(StompFrameHandler.class));
        verify(session).subscribe(eq("/user/queue/system"), any(StompFrameHandler.class));
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
                "m1", "alice-bob", "bob", "alice",
                "hi", 1L, "SENT");

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
    void shouldDispatchErrorFrameToOnAppErrorCallback() {
        handler.afterConnected(session, headers);
        ArgumentCaptor<StompFrameHandler> captor = ArgumentCaptor.forClass(StompFrameHandler.class);
        verify(session).subscribe(eq("/user/queue/errors"), captor.capture());
        StompFrameHandler errorFrameHandler = captor.getValue();
        ErrorDto err = new ErrorDto(
                "MISSING_RECIPIENT",
                "Field 'recipientUsername' is required",
                "m1");

        errorFrameHandler.handleFrame(headers, err);

        assertEquals(1, receivedAppErrors.size());
        assertEquals("MISSING_RECIPIENT", receivedAppErrors.getFirst().code());
    }

    @Test
    void shouldDispatchSystemFrameToOnSystemMessageCallback() {
        handler.afterConnected(session, headers);
        ArgumentCaptor<StompFrameHandler> captor = ArgumentCaptor.forClass(StompFrameHandler.class);
        verify(session).subscribe(eq("/user/queue/system"), captor.capture());
        StompFrameHandler systemFrameHandler = captor.getValue();
        SystemMessageDto msg = new SystemMessageDto(
                "FORCE_RECONNECT", "BACKBONE_RECOVERED");

        systemFrameHandler.handleFrame(headers, msg);

        assertEquals(1, receivedSystemMessages.size());
        assertEquals("FORCE_RECONNECT", receivedSystemMessages.getFirst().type());
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
