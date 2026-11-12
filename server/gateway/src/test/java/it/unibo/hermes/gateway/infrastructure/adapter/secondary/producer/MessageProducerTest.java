package it.unibo.hermes.gateway.infrastructure.adapter.secondary.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.configuration.JacksonConfig;
import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.domain.model.MessageStatus;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.TimeoutException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageProducerTest {

    private static final String CREATED_TOPIC = "message-created";
    private static final Duration TIMEOUT = Duration.ofSeconds(1);
    private final ObjectMapper objectMapper = new JacksonConfig().objectMapper();
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private SendResult<String, String> sendResult;
    private MessageProducer producer;

    @BeforeEach
    void setUp() {
        producer = new MessageProducer(kafkaTemplate, objectMapper, CREATED_TOPIC);
    }

    @Test
    void publishSuccess() {
        UUID messageId = UUID.randomUUID();
        Message event = new Message(
                messageId, "alice-bob",
                "alice", "bob", "hi",
                1L, MessageStatus.PENDING);
        CompletableFuture<SendResult<String, String>> future =
                CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("alice-bob"), anyString()))
                .thenReturn(future);
        producer.publish(event, TIMEOUT);

        verify(kafkaTemplate).send(eq(CREATED_TOPIC), eq("alice-bob"), anyString());
    }

    @Test
    void publishKafkaFailureThrowsBackboneUnavailableException() {
        UUID messageId = UUID.randomUUID();
        Message event = new Message(
                messageId, "alice-bob",
                "alice", "bob", "hi",
                1L, MessageStatus.PENDING);

        CompletableFuture<SendResult<String, String>> failedFuture =
                new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker unreachable"));
        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("alice-bob"), anyString()))
                .thenReturn(failedFuture);

        assertThatThrownBy(() -> producer.publish(event, TIMEOUT))
                .isInstanceOf(BackboneUnavailableException.class)
                .hasMessageContaining("Failed to publish message " + messageId + " to Kafka");
        verify(kafkaTemplate).send(eq(CREATED_TOPIC), eq("alice-bob"), anyString());
    }

    @Test
    void waitTimeoutIsReportedAsUnknownOutcomeBecauseTheRecordMayStillBePublished() {
        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("alice-bob"), anyString()))
                .thenReturn(new CompletableFuture<>()); // never completes

        assertThatThrownBy(() -> producer.publish(sample(), Duration.ofMillis(50)))
                .isInstanceOfSatisfying(BackboneUnavailableException.class,
                        e -> assertThat(e.isOutcomeUnknown()).isTrue());
    }

    @Test
    void kafkaClientDeliveryTimeoutIsAlsoAnUnknownOutcome() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new TimeoutException("delivery timeout"));
        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("alice-bob"), anyString())).thenReturn(failed);

        assertThatThrownBy(() -> producer.publish(sample(), TIMEOUT))
                .isInstanceOfSatisfying(BackboneUnavailableException.class,
                        e -> assertThat(e.isOutcomeUnknown()).isTrue());
    }

    @Test
    void brokerRejectionIsAConfirmedFailure() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RecordTooLargeException("too large"));
        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("alice-bob"), anyString())).thenReturn(failed);

        assertThatThrownBy(() -> producer.publish(sample(), TIMEOUT))
                .isInstanceOfSatisfying(BackboneUnavailableException.class,
                        e -> assertThat(e.isOutcomeUnknown()).isFalse());
    }

    @Test
    void synchronousSendFailureIsAConfirmedFailure() {
        when(kafkaTemplate.send(eq(CREATED_TOPIC), eq("alice-bob"), anyString()))
                .thenThrow(new IllegalStateException("producer closed"));

        assertThatThrownBy(() -> producer.publish(sample(), TIMEOUT))
                .isInstanceOfSatisfying(BackboneUnavailableException.class,
                        e -> assertThat(e.isOutcomeUnknown()).isFalse());
    }

    private Message sample() {
        return new Message(
                UUID.randomUUID(), "alice-bob",
                "alice", "bob",
                "hi", 1L, MessageStatus.PENDING);
    }
}
