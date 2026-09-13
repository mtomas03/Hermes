package it.unibo.hermes.gateway.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.gateway.event.MessageAckEvent;
import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.producer.MessageAckProducer;
import it.unibo.hermes.gateway.security.WebSocketJwtHandshakeInterceptor;
import it.unibo.hermes.gateway.service.InboundMessageService;
import it.unibo.hermes.gateway.service.PresenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketHandlerTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private WebSocketSessionRegistry registry;

    @Mock
    private PresenceService presenceService;

    @Mock
    private InboundMessageService publisherService;

    @Mock
    private MessageAckProducer messageAckProducer;

    @Mock
    private WebSocketSession session;

    private ChatWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChatWebSocketHandler(
                registry,
                presenceService,
                publisherService,
                messageAckProducer,
                mapper
        );

        Map<String, Object> attrs = new HashMap<>();
        attrs.put(WebSocketJwtHandshakeInterceptor.SESSION_ATTR_USERNAME, "alice");
        lenient().when(session.getAttributes()).thenReturn(attrs);
        lenient().when(session.isOpen()).thenReturn(true);
    }

    private String captureOutgoingPayload() throws Exception {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session, atLeastOnce()).sendMessage(captor.capture());
        return captor.getValue().getPayload();
    }

    @Test
    void shouldRegisterSessionAndGoOnlineWhenConnectionEstablished() {
        handler.afterConnectionEstablished(session);

        verify(registry).register("alice", session);
        verify(presenceService).setOnline(eq("alice"), any());
    }

    @Test
    void shouldUnregisterAndGoOfflineWhenConnectionCloses() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        verify(registry).unregister("alice");
        verify(presenceService).setOffline("alice");
    }

    @Test
    void shouldHandleTransportError() {
        assertThatCode(() -> handler.handleTransportError(session, new RuntimeException("Socket drop")))
                .doesNotThrowAnyException();
    }

    @Test
    void pingShouldRecordHeartbeatAndReplyWithPong() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"PING\"}"));

        verify(registry).recordHeartbeat("alice");
        verify(presenceService).refreshTtl("alice");
        assertThat(captureOutgoingPayload()).contains("\"PONG\"");
    }

    @Test
    void validSendMessageShouldBePublishedAndAcknowledged() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageEvent accepted = new MessageEvent(
                messageId, "alice-bob", "alice", "bob", "hi", 1L
        );

        when(publisherService.publish(any(), eq("alice"))).thenReturn(accepted);
        String json = "{\"type\":\"SEND_MESSAGE\",\"recipientUsername\":\"bob\",\"content\":\"hi\"}";

        handler.handleTextMessage(session, new TextMessage(json));

        verify(publisherService).publish(any(), eq("alice"));
        String response = captureOutgoingPayload();
        assertThat(response).contains("MESSAGE_ACCEPTED").contains(messageId.toString());
    }

    @Test
    void sendMessageMissingRecipientShouldBeRejected() throws Exception {
        String json = "{\"type\":\"SEND_MESSAGE\",\"content\":\"hi\"}";

        handler.handleTextMessage(session, new TextMessage(json));

        verifyNoInteractions(publisherService);
        assertThat(captureOutgoingPayload()).contains("MISSING_RECIPIENT");
    }

    @Test
    void sendMessageMissingContentShouldBeRejected() throws Exception {
        String json = "{\"type\":\"SEND_MESSAGE\",\"recipientUsername\":\"bob\"}";

        handler.handleTextMessage(session, new TextMessage(json));

        verifyNoInteractions(publisherService);
        assertThat(captureOutgoingPayload()).contains("MISSING_CONTENT");
    }

    @Test
    void sendingMessageToSelfShouldBeRejected() throws Exception {
        String json = "{\"type\":\"SEND_MESSAGE\",\"recipientUsername\":\"alice\",\"content\":\"hi\"}";

        handler.handleTextMessage(session, new TextMessage(json));

        verifyNoInteractions(publisherService);
        assertThat(captureOutgoingPayload()).contains("SELF_SEND");
    }

    @Test
    void deliveryRejectionShouldBeReportedWhenPersistenceUnavailable() throws Exception {
        when(publisherService.publish(any(), eq("alice")))
                .thenThrow(new PersistenceUnavailableException("down", new RuntimeException()));
        String json = "{\"type\":\"SEND_MESSAGE\",\"recipientUsername\":\"bob\",\"content\":\"hi\"}";

        handler.handleTextMessage(session, new TextMessage(json));

        assertThat(captureOutgoingPayload()).contains("DELIVERY_REJECTED");
    }

    @Test
    void unexpectedErrorShouldReturnInternalError() throws Exception {
        when(publisherService.publish(any(), eq("alice")))
                .thenThrow(new RuntimeException("Unexpected NPE"));
        String json = "{\"type\":\"SEND_MESSAGE\",\"recipientUsername\":\"bob\",\"content\":\"hi\"}";

        handler.handleTextMessage(session, new TextMessage(json));

        assertThat(captureOutgoingPayload()).contains("INTERNAL_ERROR");
    }

    @Test
    void ackShouldPublishToKafkaProducerWithoutWsReply() throws Exception {
        String json = "{\"type\":\"ACK\",\"messageId\":\"msg-123\",\"conversationId\":\"alice-bob\",\"senderUsername\":\"bob\",\"logicalTimestamp\":10}";

        handler.handleTextMessage(session, new TextMessage(json));

        ArgumentCaptor<MessageAckEvent> captor = ArgumentCaptor.forClass(MessageAckEvent.class);
        verify(messageAckProducer).publishAck(captor.capture());

        MessageAckEvent event = captor.getValue();
        assertThat(event.messageId()).isEqualTo("msg-123");
        assertThat(event.conversationId()).isEqualTo("alice-bob");
        assertThat(event.senderUsername()).isEqualTo("bob");
        assertThat(event.recipientUsername()).isEqualTo("alice");
        assertThat(event.logicalTimestamp()).isEqualTo(10L);

        verifyNoInteractions(publisherService);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void malformedJsonShouldProduceMalformedMessageError() throws Exception {
        handler.handleTextMessage(session, new TextMessage("not-json-at-all"));

        assertThat(captureOutgoingPayload()).contains("MALFORMED_MESSAGE");
    }

    @Test
    void missingTypeShouldProduceMissingTypeError() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"content\":\"hi\"}"));

        assertThat(captureOutgoingPayload()).contains("MISSING_TYPE");
    }

    @Test
    void unknownTypeShouldProduceUnknownTypeError() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"RECEIVE_MESSAGE\"}"));

        assertThat(captureOutgoingPayload()).contains("UNKNOWN_TYPE");
    }
}
