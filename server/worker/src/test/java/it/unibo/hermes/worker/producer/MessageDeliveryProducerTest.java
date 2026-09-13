package it.unibo.hermes.worker.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.worker.event.MessageEvent;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageDeliveryProducerTest {

    private static final String DELIVERY_TOPIC = "message-delivery";
    private static final String GATEWAY_ID = "gateway-1";

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private MessageDeliveryProducer producer;

    @BeforeEach
    void setUp() {
        producer = new MessageDeliveryProducer(kafkaTemplate, objectMapper, DELIVERY_TOPIC);
    }

    private MessageEvent createSampleEvent() {
        return new MessageEvent(
                UUID.randomUUID().toString(),
                "alice-bob",
                "alice", "bob", "hi",
                1L);
    }

    @SuppressWarnings("unchecked")
    private SendResult<String, String> createSuccessfulSendResult() {
        SendResult<String, String> result = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(42L);
        when(result.getRecordMetadata()).thenReturn(metadata);
        return result;
    }

    @Test
    void shouldPublishSerializedDeliveryEventKeyedByGatewayId() {
        MessageEvent source = createSampleEvent();
        SendResult<String, String> sendResult = createSuccessfulSendResult();
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);
        when(kafkaTemplate.send(eq(DELIVERY_TOPIC), eq(GATEWAY_ID), anyString()))
                .thenReturn(future);

        producer.publishDeliveryEvent(source, GATEWAY_ID);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(DELIVERY_TOPIC), eq(GATEWAY_ID), payloadCaptor.capture());
        String payload = payloadCaptor.getValue();
        assertThat(payload)
                .contains(source.messageId())
                .contains(source.conversationId())
                .contains(source.senderUsername())
                .contains(source.recipientUsername())
                .contains(GATEWAY_ID)
                .contains("hi");
    }

    @Test
    void shouldThrowIllegalStateExceptionWhenJsonSerializationFails() throws Exception {
        ObjectMapper spyObjectMapper = spy(objectMapper);
        MessageDeliveryProducer customProducer = new MessageDeliveryProducer(
                kafkaTemplate, spyObjectMapper, DELIVERY_TOPIC);
        MessageEvent source = createSampleEvent();
        doThrow(new JsonProcessingException("JSON mapping error") {
        })
                .when(spyObjectMapper).writeValueAsString(any());

        assertThatThrownBy(() -> customProducer.publishDeliveryEvent(source, GATEWAY_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize MessageDeliveryEvent for message " + source.messageId());

        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void shouldThrowRuntimeExceptionWhenKafkaSendFails() {
        MessageEvent source = createSampleEvent();
        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka broker unreachable"));

        when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

        assertThatThrownBy(() -> producer.publishDeliveryEvent(source, GATEWAY_ID))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to publish delivery event for message " + source.messageId());
    }
}
