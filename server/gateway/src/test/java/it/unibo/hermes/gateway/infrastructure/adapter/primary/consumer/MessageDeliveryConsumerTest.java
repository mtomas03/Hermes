package it.unibo.hermes.gateway.infrastructure.adapter.primary.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.application.model.DeliverMessageCommand;
import it.unibo.hermes.gateway.application.port.primary.MessageDeliveryPort;
import it.unibo.hermes.gateway.infrastructure.event.MessageDeliveryEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryConsumerTest {

    private static final String TOPIC = "message-delivery";

    @Mock
    private MessageDeliveryPort messageDeliveryPort;
    @Mock
    private ObjectMapper objectMapper;

    private MessageDeliveryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageDeliveryConsumer(messageDeliveryPort, objectMapper);
    }

    @Test
    void shouldMapEventToCommandAndDelegateToDeliveryPort() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String rawJson = "{\"messageId\":\"" + messageId + "\"}";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 0L, "gateway-1", rawJson);
        when(objectMapper.readValue(rawJson, MessageDeliveryEvent.class)).thenReturn(new MessageDeliveryEvent(
                messageId, "alice-bob",
                "alice", "bob",
                "gateway-1", "hi", 1L));

        consumer.consume(record);

        verify(messageDeliveryPort).deliver(new DeliverMessageCommand(
                messageId, "alice-bob",
                "alice", "bob",
                "gateway-1", "hi", 1L));
    }

    @Test
    void consumeDeserializationFailure() throws Exception {
        String invalidJson = "invalid-json-payload";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                TOPIC, 0, 0L, "gateway-1", invalidJson);
        when(objectMapper.readValue(invalidJson, MessageDeliveryEvent.class))
                .thenThrow(new JsonProcessingException("Deserialization error") {
                });

        assertThatThrownBy(() -> consumer.consume(record))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot deserialize MessageDeliveryEvent");
        verifyNoInteractions(messageDeliveryPort);
    }
}
