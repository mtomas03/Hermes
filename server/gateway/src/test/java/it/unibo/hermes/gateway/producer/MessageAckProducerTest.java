package it.unibo.hermes.gateway.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.event.MessageAckEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageAckProducerTest {

    private static final String ACK_TOPIC = "message-acknowledged";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
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
        verify(kafkaTemplate).send(ACK_TOPIC, "bob", serializedJson);
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
        verify(kafkaTemplate).send(ACK_TOPIC, "bob", serializedJson);
    }
}
