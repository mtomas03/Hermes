package it.unibo.hermes.worker.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.unibo.hermes.worker.event.MessageAckEvent;
import it.unibo.hermes.worker.service.AcknowledgementService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
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
class MessageAckConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Mock
    private AcknowledgementService acknowledgementService;

    private MessageAckConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageAckConsumer(acknowledgementService, objectMapper);
    }

    private ConsumerRecord<String, String> createRecord(String jsonPayload) {
        return new ConsumerRecord<>("message-acknowledged", 0, 0L, "bob", jsonPayload);
    }

    @Test
    void shouldDeserializeAndForwardValidEventToAcknowledgementService() throws Exception {
        String messageId = UUID.randomUUID().toString();
        MessageAckEvent expectedEvent = new MessageAckEvent(
                messageId,
                "alice-bob",
                "alice",
                "bob",
                1L
        );
        String jsonPayload = objectMapper.writeValueAsString(expectedEvent);

        consumer.consume(createRecord(jsonPayload));

        ArgumentCaptor<MessageAckEvent> captor = ArgumentCaptor.forClass(MessageAckEvent.class);
        verify(acknowledgementService).processAcknowledgement(captor.capture());
        MessageAckEvent actualEvent = captor.getValue();
        assertThat(actualEvent.messageId()).isEqualTo(messageId);
        assertThat(actualEvent.conversationId()).isEqualTo("alice-bob");
        assertThat(actualEvent.senderUsername()).isEqualTo("alice");
        assertThat(actualEvent.recipientUsername()).isEqualTo("bob");
        assertThat(actualEvent.logicalTimestamp()).isEqualTo(1L);
    }

    @Test
    void shouldThrowAndNotProcessWhenPayloadIsMalformed() {
        ConsumerRecord<String, String> malformedRecord = createRecord("{ invalid json syntax ");

        assertThatThrownBy(() -> consumer.consume(malformedRecord))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot deserialize MessageAckEvent");

        verifyNoInteractions(acknowledgementService);
    }
}
