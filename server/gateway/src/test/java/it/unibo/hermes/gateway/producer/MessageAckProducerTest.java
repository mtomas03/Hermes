package it.unibo.hermes.gateway.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import it.unibo.hermes.gateway.config.JacksonConfig;
import it.unibo.hermes.gateway.event.MessageAckEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageAckProducerTest {

    private static final String ACK_TOPIC = "message-acknowledged";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private ObjectMapper objectMapper;

    private MessageAckProducer producer;

    @BeforeEach
    void setUp() {
        producer = new MessageAckProducer(kafkaTemplate, objectMapper, ACK_TOPIC);
    }

    @Test
    void publishAckSuccess() throws Exception {
        String messageId = UUID.randomUUID().toString();
        MessageAckEvent ackEvent = new MessageAckEvent(messageId, "bob");
        String serializedJson = "{\"messageId\":\"" + messageId + "\",\"recipientUsername\":\"bob\"}";

        when(objectMapper.writeValueAsString(ackEvent)).thenReturn(serializedJson);
        producer.publishAck(ackEvent);

        verify(objectMapper).writeValueAsString(ackEvent);
        verify(kafkaTemplate).send(ACK_TOPIC, messageId, serializedJson);
    }

    @Test
    void publishAckSerializationErrorHandledGracefully() throws Exception {
        String messageId = UUID.randomUUID().toString();
        MessageAckEvent ackEvent = new MessageAckEvent(messageId, "bob");

        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Failed to serialize") {
                });

        assertThatCode(() -> producer.publishAck(ackEvent))
                .doesNotThrowAnyException();
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publishAckKafkaSendErrorHandledGracefully() throws Exception {
        String messageId = UUID.randomUUID().toString();
        MessageAckEvent ackEvent = new MessageAckEvent(messageId, "bob");
        String serializedJson = "{\"messageId\":\"" + messageId + "\"}";

        when(objectMapper.writeValueAsString(ackEvent)).thenReturn(serializedJson);
        doThrow(new RuntimeException("Kafka broker connection timeout"))
                .when(kafkaTemplate).send(anyString(), anyString(), anyString());

        assertThatCode(() -> producer.publishAck(ackEvent))
                .doesNotThrowAnyException();
        verify(kafkaTemplate).send(ACK_TOPIC, messageId, serializedJson);
    }

    /**
     * Pins the wire contract consumed by the Worker's {@code MessageAckConsumer}: the record value must be
     * a plain JSON object (not a JSON-encoded string) and the record key must be the messageId.
     */
    @Test
    void publishAckSendsPlainJsonObjectKeyedByMessageId() throws Exception {
        ObjectMapper realMapper = new JacksonConfig().objectMapper();
        MessageAckProducer realProducer = new MessageAckProducer(kafkaTemplate, realMapper, ACK_TOPIC);
        String messageId = UUID.randomUUID().toString();

        realProducer.publishAck(new MessageAckEvent(messageId, "bob"));

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(ACK_TOPIC), eq(messageId), payload.capture());
        JsonNode json = realMapper.readTree(payload.getValue());
        assertThat(json.isObject()).isTrue();
        assertThat(json.get("messageId").asText()).isEqualTo(messageId);
        assertThat(json.get("recipientUsername").asText()).isEqualTo("bob");
    }
}
