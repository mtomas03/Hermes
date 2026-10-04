package it.unibo.hermes.gateway.producer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.config.JacksonConfig;
import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.BackboneUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageProducerTest {

    private static final String CREATED_TOPIC = "message-created";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private SendResult<String, String> sendResult;

    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();

    private MessageProducer producer;

    @BeforeEach
    void setUp() {
        producer = new MessageProducer(kafkaTemplate, objectMapper, CREATED_TOPIC);
    }

    @Test
    void publishSuccess() {
        UUID messageId = UUID.randomUUID();
        MessageEvent event = new MessageEvent(
                messageId, "alice-bob",
                "alice", "bob", "hi",
                1L
        );
        CompletableFuture<SendResult<String, String>> future =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("alice-bob"), anyString()))
                .thenReturn(future);
        producer.publish(event);

        verify(kafkaTemplate).send(eq(CREATED_TOPIC), eq("alice-bob"), anyString());
    }

    /**
     * Pins the wire contract consumed by the Worker's {@code MessageCreatedConsumer}: the record value
     * must be a plain JSON object (not a JSON-encoded string) with the expected field names, and the
     * record key must be the conversationId.
     */
    @Test
    void publishSendsPlainJsonObjectKeyedByConversationId() throws Exception {
        UUID messageId = UUID.randomUUID();
        MessageEvent event = new MessageEvent(messageId, "alice-bob", "alice", "bob", "hi", 7L);
        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("alice-bob"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.publish(event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(CREATED_TOPIC), eq("alice-bob"), payload.capture());
        JsonNode json = objectMapper.readTree(payload.getValue());
        assertThat(json.isObject()).isTrue();
        assertThat(json.get("messageId").asText()).isEqualTo(messageId.toString());
        assertThat(json.get("conversationId").asText()).isEqualTo("alice-bob");
        assertThat(json.get("senderUsername").asText()).isEqualTo("alice");
        assertThat(json.get("recipientUsername").asText()).isEqualTo("bob");
        assertThat(json.get("content").asText()).isEqualTo("hi");
        assertThat(json.get("logicalTimestamp").asLong()).isEqualTo(7L);
    }

    @Test
    void publishKafkaFailureThrowsBackboneUnavailableException() {
        UUID messageId = UUID.randomUUID();
        MessageEvent event = new MessageEvent(
                messageId, "alice-bob",
                "alice", "bob", "hi",
                1L
        );

        CompletableFuture<SendResult<String, String>> failedFuture =
                new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker unreachable"));
        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("alice-bob"), anyString()))
                .thenReturn(failedFuture);

        assertThatThrownBy(() -> producer.publish(event))
                .isInstanceOf(BackboneUnavailableException.class)
                .hasMessageContaining("Failed to publish message " + messageId + " to Kafka");
        verify(kafkaTemplate).send(eq(CREATED_TOPIC), eq("alice-bob"), anyString());
    }
}
