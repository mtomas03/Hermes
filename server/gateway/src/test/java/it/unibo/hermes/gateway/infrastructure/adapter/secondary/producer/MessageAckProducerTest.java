package it.unibo.hermes.gateway.infrastructure.adapter.secondary.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.infrastructure.event.MessageAckEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageAckProducerTest {

    private static final String ACK_TOPIC = "message-acknowledged";
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private SendResult<String, String> sendResult;

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
        when(kafkaTemplate.send(ACK_TOPIC, messageId, serializedJson))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.publishAck(messageId, "bob", TIMEOUT);

        verify(objectMapper).writeValueAsString(ackEvent);
        verify(kafkaTemplate).send(ACK_TOPIC, messageId, serializedJson);
    }

    @Test
    void publishAckSerializationErrorIsAnInternalErrorNotABackboneOutage() throws Exception {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Failed to serialize") {
                });

        assertThatThrownBy(() -> producer.publishAck(UUID.randomUUID().toString(), "bob", TIMEOUT))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void publishAckSendFailureIsReportedInsteadOfBeingSwallowed() throws Exception {
        String messageId = UUID.randomUUID().toString();
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        assertThatThrownBy(() -> producer.publishAck(messageId, "bob", TIMEOUT))
                .isInstanceOfSatisfying(BackboneUnavailableException.class, e -> {
                    assertThat(e.isOutcomeUnknown()).isFalse();
                    assertThat(e.getMessage()).contains(messageId);
                });
    }

    @Test
    void publishAckSynchronousSendFailureIsReported() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Kafka broker connection timeout"));

        assertThatThrownBy(() -> producer.publishAck(UUID.randomUUID().toString(), "bob", TIMEOUT))
                .isInstanceOf(BackboneUnavailableException.class);
    }

    @Test
    void publishAckWaitTimeoutIsAnUnknownOutcome() throws Exception {
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        assertThatThrownBy(() -> producer.publishAck(UUID.randomUUID().toString(), "bob", Duration.ofMillis(50)))
                .isInstanceOfSatisfying(BackboneUnavailableException.class,
                        e -> assertThat(e.isOutcomeUnknown()).isTrue());
    }
}
