package it.unibo.hermes.worker.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.worker.event.MessageEvent;
import it.unibo.hermes.worker.service.DeliveryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class MessageCreatedConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Mock
    private DeliveryService deliveryService;

    private MessageCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageCreatedConsumer(deliveryService, objectMapper);
    }

    private ConsumerRecord<String, String> createRecord(String jsonPayload) {
        return new ConsumerRecord<>("message-created", 0, 0L, "alice-bob", jsonPayload);
    }

    @Test
    void shouldDeserializeAndForwardValidEventToDeliveryService() throws Exception {
        String messageId = UUID.randomUUID().toString();
        MessageEvent expectedEvent = new MessageEvent(
                messageId,
                "alice-bob",
                "alice",
                "bob",
                "hi",
                1L
        );
        String jsonPayload = objectMapper.writeValueAsString(expectedEvent);

        consumer.consume(createRecord(jsonPayload));

        ArgumentCaptor<MessageEvent> captor = ArgumentCaptor.forClass(MessageEvent.class);
        verify(deliveryService).processMessage(captor.capture());
        MessageEvent actualEvent = captor.getValue();
        assertThat(actualEvent.messageId()).isEqualTo(messageId);
        assertThat(actualEvent.conversationId()).isEqualTo("alice-bob");
        assertThat(actualEvent.senderUsername()).isEqualTo("alice");
        assertThat(actualEvent.recipientUsername()).isEqualTo("bob");
        assertThat(actualEvent.content()).isEqualTo("hi");
        assertThat(actualEvent.logicalTimestamp()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException and skip processing when JSON is malformed")
    void shouldThrowAndNotProcessWhenPayloadIsMalformed() {
        // Given
        ConsumerRecord<String, String> malformedRecord = createRecord("not-valid-json");

        // When / Then
        assertThatThrownBy(() -> consumer.consume(malformedRecord))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot deserialize MessageEvent");

        verifyNoInteractions(deliveryService);
    }
}
