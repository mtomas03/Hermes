package it.unibo.hermes.gateway.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.dto.MessageFromGatewayDto;
import it.unibo.hermes.gateway.event.MessageDeliveryEvent;
import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryConsumerTest {

    private static final String CURRENT_GATEWAY_ID = "gateway-1";
    private static final String TOPIC = "message-delivery";

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private WebSocketSessionRegistry sessionRegistry;
    @Mock
    private ObjectMapper objectMapper;

    private MessageDeliveryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageDeliveryConsumer(messagingTemplate, sessionRegistry, objectMapper, CURRENT_GATEWAY_ID);
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
        when(sessionRegistry.isConnected("bob")).thenReturn(true);

        consumer.consume(record);

        ArgumentCaptor<MessageFromGatewayDto> payloadCaptor = ArgumentCaptor.forClass(MessageFromGatewayDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/messages"), payloadCaptor.capture());
        MessageFromGatewayDto payload = payloadCaptor.getValue();
        assertThat(payload.messageId()).isEqualTo(messageId);
        assertThat(payload.recipientUsername()).isEqualTo("bob");
        assertThat(payload.senderUsername()).isEqualTo("alice");
        assertThat(payload.content()).isEqualTo("hi");
        assertThat(payload.messageStatus()).isEqualTo("DELIVERED");
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
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void consumeRecipientNotConnected() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String rawJson = "{\"messageId\":\"" + messageId + "\",\"gatewayId\":\"gateway-1\",\"recipientUsername\":\"bob\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, "gateway-1", rawJson);
        MessageDeliveryEvent event = new MessageDeliveryEvent(
                messageId, "alice-bob", "alice", "bob",
                "gateway-1", "hi", 1L
        );

        when(objectMapper.readValue(rawJson, MessageDeliveryEvent.class)).thenReturn(event);
        when(sessionRegistry.isConnected("bob")).thenReturn(false);
        consumer.consume(record);

        verify(sessionRegistry).isConnected("bob");
        verifyNoInteractions(messagingTemplate);
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
    void consumeSendErrorHandledGracefully() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String rawJson = "{\"messageId\":\"" + messageId + "\",\"gatewayId\":\"gateway-1\",\"recipientUsername\":\"bob\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(TOPIC, 0, 0L, "gateway-1", rawJson);

        MessageDeliveryEvent event = new MessageDeliveryEvent(
                messageId, "alice-bob", "alice", "bob",
                "gateway-1", "hi", 1L
        );

        when(objectMapper.readValue(rawJson, MessageDeliveryEvent.class)).thenReturn(event);
        when(sessionRegistry.isConnected("bob")).thenReturn(true);
        doThrow(new RuntimeException("Broker unavailable"))
                .when(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/messages"), any());

        assertThatCode(() -> consumer.consume(record))
                .doesNotThrowAnyException();
        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/messages"), any());
    }
}
