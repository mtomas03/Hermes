package it.unibo.hermes.worker.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.event.MessageCreatedEvent;
import org.apache.kafka.clients.producer.RecordMetadata;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryEventProducerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    private DeliveryEventProducer producer;

    @BeforeEach
    void setUp() {
        producer = new DeliveryEventProducer(
                kafkaTemplate, objectMapper, "message-delivery");
    }

    private MessageCreatedEvent event() {
        return new MessageCreatedEvent(
                UUID.randomUUID().toString(),
                "alice-bob",
                "alice", "bob", "hi",
                1L, System.currentTimeMillis());
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, String> successfulSendResult() {
        SendResult<String, String> result = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(42L);
        when(result.getRecordMetadata()).thenReturn(metadata);
        return result;
    }

    @Test
    void shouldPublishSerializedDeliveryEventKeyedByGatewayId() {
        MessageCreatedEvent source = event();
        SendResult<String, String> sendResult = successfulSendResult();
        when(kafkaTemplate.send(eq("message-delivery"), eq("gw-1"), anyString()))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        producer.publishDeliveryEvent(source, "gw-1");

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("message-delivery"), eq("gw-1"), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThatJsonContainsMessageId(payload, source.messageId());
    }

    private void assertThatJsonContainsMessageId(String json, String messageId) {
        org.assertj.core.api.Assertions.assertThat(json).contains(messageId);
    }

    @Test
    void shouldThrowWhenKafkaSendFails() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unreachable"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failed);

        assertThatThrownBy(() -> producer.publishDeliveryEvent(event(), "gw-1"))
                .isInstanceOf(RuntimeException.class);
    }
}
