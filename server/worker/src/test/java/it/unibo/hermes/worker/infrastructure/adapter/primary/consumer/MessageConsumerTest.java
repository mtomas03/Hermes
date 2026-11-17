package it.unibo.hermes.worker.infrastructure.adapter.primary.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.application.port.primary.DeliveryPort;
import it.unibo.hermes.worker.domain.exception.InvalidMessageException;
import it.unibo.hermes.worker.domain.model.Message;
import it.unibo.hermes.worker.infrastructure.event.MessageEvent;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageConsumerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Mock
    private DeliveryPort deliveryPort;

    private MessageConsumer consumer;

    private static String json(String messageId, String extraProperties) {
        return "{\"messageId\":\"" + messageId + "\",\"conversationId\":\"alice-bob\","
                + "\"senderUsername\":\"alice\",\"recipientUsername\":\"bob\",\"content\":\"hi\","
                + "\"logicalTimestamp\":1" + extraProperties + "}";
    }

    @BeforeEach
    void setUp() {
        consumer = new MessageConsumer(deliveryPort, objectMapper);
    }

    private ConsumerRecord<String, String> createRecord(String jsonPayload) {
        return new ConsumerRecord<>("message-created", 0, 0L, "alice-bob", jsonPayload);
    }

    @Test
    void shouldDeserializeMapToTheDomainAndInvokeTheDeliveryUseCase() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String json = objectMapper.writeValueAsString(
                new MessageEvent(
                        messageId, "alice-bob",
                        "alice", "bob",
                        "hi", 1L));

        consumer.consume(createRecord(json));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(deliveryPort).processMessage(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new Message(
                messageId, "alice-bob",
                "alice", "bob",
                "hi", 1L));
    }

    @Test
    void unknownPropertiesInTheEventAreIgnored() {
        consumer.consume(createRecord(json(
                UUID.randomUUID().toString(),
                ",\"extra\":true")));

        verify(deliveryPort).processMessage(any(Message.class));
    }

    @Test
    void shouldThrowIllegalArgumentExceptionAndNotProcessWhenJSONPayloadIsMalformed() {
        ConsumerRecord<String, String> malformedRecord = createRecord("not-valid-json");

        assertThatThrownBy(() -> consumer.consume(malformedRecord))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot deserialize MessageEvent");

        verifyNoInteractions(deliveryPort);
    }

    @Test
    void eventWithoutLogicalTimestampIsRejectedByTheDomainBeforeReachingTheUseCase() {
        String json = "{\"messageId\":\"" + UUID.randomUUID() + "\",\"conversationId\":\"alice-bob\","
                + "\"senderUsername\":\"alice\",\"recipientUsername\":\"bob\",\"content\":\"hi\"}";

        assertThatThrownBy(() -> consumer.consume(createRecord(json)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessageContaining("logicalTimestamp");

        verifyNoInteractions(deliveryPort);
    }

    @Test
    void failuresOfTheUseCaseArePropagatedToTheContainerForRetryHandling() {
        doThrow(new IllegalStateException("failure"))
                .when(deliveryPort).processMessage(any(Message.class));

        assertThatThrownBy(() -> consumer.consume(createRecord(
                json(UUID.randomUUID().toString(), ""))))
                .isInstanceOf(IllegalStateException.class);
    }
}
