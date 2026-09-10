package it.unibo.hermes.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.event.MessageCreatedEvent;
import it.unibo.hermes.worker.service.DeliveryService;
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
class MessageCreatedConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @Mock
    private DeliveryService deliveryService;
    private MessageCreatedConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageCreatedConsumer(deliveryService, objectMapper);
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("message-created", 0, 0L, "key", json);
    }

    @Test
    void shouldDeserializeAndForwardValidEventToDeliveryService() throws Exception {
        MessageCreatedEvent event = new MessageCreatedEvent(
                UUID.randomUUID().toString(), "alice-bob",
                "alice", "bob", "hi", 1L, System.currentTimeMillis());
        String json = objectMapper.writeValueAsString(event);

        consumer.consume(record(json));

        ArgumentCaptor<MessageCreatedEvent> captor = ArgumentCaptor.forClass(MessageCreatedEvent.class);
        verify(deliveryService).processMessage(captor.capture());
        assertThat(captor.getValue().messageId()).isEqualTo(event.messageId());
        assertThat(captor.getValue().conversationId()).isEqualTo("alice-bob");
        assertThat(captor.getValue().recipientUsername()).isEqualTo("bob");
    }

    @Test
    void shouldThrowAndNotProcessWhenPayloadIsMalformed() {
        assertThatThrownBy(() -> consumer.consume(record("not-valid-json")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(deliveryService);
    }
}
