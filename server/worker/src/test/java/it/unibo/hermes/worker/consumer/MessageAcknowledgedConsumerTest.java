package it.unibo.hermes.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.event.MessageAcknowledgedEvent;
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
class MessageAcknowledgedConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private AcknowledgementService acknowledgementService;
    private MessageAcknowledgedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageAcknowledgedConsumer(acknowledgementService, objectMapper);
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("message-acknowledged", 0, 0L, "key", json);
    }

    @Test
    void shouldDeserializeAndForwardValidEventToAcknowledgementService() {
        MessageAcknowledgedEvent event = new MessageAcknowledgedEvent(
                UUID.randomUUID().toString(), "alice-bob",
                "bob", System.currentTimeMillis());
        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        consumer.consume(record(json));

        ArgumentCaptor<MessageAcknowledgedEvent> captor = ArgumentCaptor.forClass(MessageAcknowledgedEvent.class);
        verify(acknowledgementService).processAcknowledgement(captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo(event.messageId());
        assertThat(captor.getValue().conversationId()).isEqualTo("alice-bob");
        assertThat(captor.getValue().recipientUsername()).isEqualTo("bob");
    }

    @Test
    void shouldThrowAndNotProcessWhenPayloadIsMalformed() {
        assertThatThrownBy(() -> consumer.consume(record("{ not json")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(acknowledgementService);
    }
}
