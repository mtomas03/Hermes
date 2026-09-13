package it.unibo.hermes.gateway.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.event.MessageDeliveryEvent;
import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryConsumerTest {

    private static final String CURRENT_GATEWAY_ID = "gateway-1";
    private static final String TOPIC = "message-delivery";

    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private WebSocketSession webSocketSession;

    private MessageDeliveryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageDeliveryConsumer(sessionRegistry, objectMapper, CURRENT_GATEWAY_ID);
    }

    @Test
    void consumeSuccessfulDelivery() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String rawJson = "{\"messageId\":\"" + messageId + "\",\"gatewayId\":\"gateway-1\",\"recipientUsername\":\"bob\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, "gateway-1", rawJson);
        MessageDeliveryEvent event = new MessageDeliveryEvent(
                messageId, "alice-bob", "alice", "bob",
                "gateway-1", "hi", 1L
        );

        when(objectMapper.readValue(rawJson, MessageDeliveryEvent.class)).thenReturn(event);
        when(sessionRegistry.sessionOf("bob")).thenReturn(Optional.of(webSocketSession));
        when(webSocketSession.isOpen()).thenReturn(true);
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"serialized\":\"payload\"}");
        consumer.consume(record);

        ArgumentCaptor<TextMessage> textMessageCaptor = ArgumentCaptor.forClass(TextMessage.class);
        verify(webSocketSession).sendMessage(textMessageCaptor.capture());
        assertThat(textMessageCaptor.getValue().getPayload()).isEqualTo("{\"serialized\":\"payload\"}");
    }

    @Test
    void consumeSkipDifferentGateway() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String rawJson = "{\"messageId\":\"" + messageId + "\",\"gatewayId\":\"gateway-2\",\"recipientUsername\":\"bob\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, "gateway-2", rawJson);

        MessageDeliveryEvent event = new MessageDeliveryEvent(
                messageId, "alice-bob", "alice", "bob",
                "gateway-2", "hi", 1L
        );

        when(objectMapper.readValue(rawJson, MessageDeliveryEvent.class)).thenReturn(event);
        consumer.consume(record);

        verifyNoInteractions(sessionRegistry);
        verifyNoInteractions(webSocketSession);
    }

    @Test
    void consumeSessionAbsent() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String rawJson = "{\"messageId\":\"" + messageId + "\",\"gatewayId\":\"gateway-1\",\"recipientUsername\":\"bob\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, "gateway-1", rawJson);
        MessageDeliveryEvent event = new MessageDeliveryEvent(
                messageId, "alice-bob", "alice", "bob",
                "gateway-1", "hi", 1L
        );

        when(objectMapper.readValue(rawJson, MessageDeliveryEvent.class)).thenReturn(event);
        when(sessionRegistry.sessionOf("bob")).thenReturn(Optional.empty());
        consumer.consume(record);

        verify(sessionRegistry).sessionOf("bob");
        verifyNoInteractions(webSocketSession);
    }

    @Test
    void consumeSessionClosed() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String rawJson = "{\"messageId\":\"" + messageId + "\",\"gatewayId\":\"gateway-1\",\"recipientUsername\":\"bob\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, "gateway-1", rawJson);
        MessageDeliveryEvent event = new MessageDeliveryEvent(
                messageId, "alice-bob", "alice", "bob",
                "gateway-1", "hi", 1L
        );

        when(objectMapper.readValue(rawJson, MessageDeliveryEvent.class)).thenReturn(event);
        when(sessionRegistry.sessionOf("bob")).thenReturn(Optional.of(webSocketSession));
        when(webSocketSession.isOpen()).thenReturn(false);
        consumer.consume(record);

        verify(webSocketSession, never()).sendMessage(any());
    }

    @Test
    void consumeDeserializationFailure() throws Exception {
        String invalidJson = "invalid-json-payload";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, "gateway-1", invalidJson);

        when(objectMapper.readValue(invalidJson, MessageDeliveryEvent.class))
                .thenThrow(new JsonProcessingException("Deserialization error") {
                });

        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot deserialize MessageDeliveryEvent");
    }

    @Test
    void consumeWebSocketSendErrorHandledGracefully() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String rawJson = "{\"messageId\":\"" + messageId + "\",\"gatewayId\":\"gateway-1\",\"recipientUsername\":\"bob\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, "gateway-1", rawJson);

        MessageDeliveryEvent event = new MessageDeliveryEvent(
                messageId, "alice-bob", "alice", "bob",
                "gateway-1", "hi", 1L
        );

        when(objectMapper.readValue(rawJson, MessageDeliveryEvent.class)).thenReturn(event);
        when(sessionRegistry.sessionOf("bob")).thenReturn(Optional.of(webSocketSession));
        when(webSocketSession.isOpen()).thenReturn(true);
        when(objectMapper.writeValueAsString(event)).thenReturn("{\"serialized\":\"payload\"}");
        doThrow(new IOException("Transport connection broken")).when(webSocketSession).sendMessage(any());

        assertThatCode(() -> consumer.consume(record))
                .doesNotThrowAnyException();
        verify(webSocketSession).sendMessage(any());
    }
}
