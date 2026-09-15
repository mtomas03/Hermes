package it.unibo.hermes.gateway.producer;

import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.BackboneUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageCreatedProducerTest {

    private static final String CREATED_TOPIC = "message-created";

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private SendResult<String, Object> sendResult;

    private MessageCreatedProducer producer;

    @BeforeEach
    void setUp() {
        producer = new MessageCreatedProducer(kafkaTemplate, CREATED_TOPIC);
    }

    @Test
    void publishSuccess() {
        UUID messageId = UUID.randomUUID();
        MessageEvent event = new MessageEvent(
                messageId, "alice-bob",
                "alice", "bob", "hi",
                1L
        );
        CompletableFuture<SendResult<String, Object>> future =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(CREATED_TOPIC, "alice-bob", event))
                .thenReturn(future);
        producer.publish(event);

        verify(kafkaTemplate).send(CREATED_TOPIC, "alice-bob", event);
    }

    @Test
    void publishKafkaFailureThrowsBackboneUnavailableException() {
        UUID messageId = UUID.randomUUID();
        MessageEvent event = new MessageEvent(
                messageId, "alice-bob",
                "alice", "bob", "hi",
                1L
        );

        CompletableFuture<SendResult<String, Object>> failedFuture =
                new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker unreachable"));
        when(kafkaTemplate.send(CREATED_TOPIC, "alice-bob", event))
                .thenReturn(failedFuture);

        assertThatThrownBy(() -> producer.publish(event))
                .isInstanceOf(BackboneUnavailableException.class)
                .hasMessageContaining("Failed to publish message " + messageId + " to Kafka");
        verify(kafkaTemplate).send(CREATED_TOPIC, "alice-bob", event);
    }
}
