package it.unibo.hermes.worker.infrastructure.adapter.primary.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.application.port.primary.AcknowledgementPort;
import it.unibo.hermes.worker.domain.model.Acknowledgement;
import it.unibo.hermes.worker.infrastructure.event.MessageAckEvent;
import it.unibo.hermes.worker.infrastructure.adapter.primary.exception.MalformedEventException;
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
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Mock
    private AcknowledgementPort acknowledgementPort;

    private MessageAckConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageAckConsumer(acknowledgementPort, objectMapper);
    }

    private ConsumerRecord<String, String> createRecord(String jsonPayload) {
        return new ConsumerRecord<>("message-acknowledged", 0, 0L, "bob", jsonPayload);
    }

    @Test
    void shouldDeserializeMapToTheDomainAndInvokeTheAcknowledgementUseCase() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String json = objectMapper.writeValueAsString(
                new MessageAckEvent(messageId, "bob"));

        consumer.consume(createRecord(json));

        ArgumentCaptor<Acknowledgement> captor = ArgumentCaptor.forClass(Acknowledgement.class);
        verify(acknowledgementPort).processAcknowledgement(captor.capture());
        assertThat(captor.getValue()).isEqualTo(
                new Acknowledgement(messageId, "bob"));
    }

    @Test
    void shouldThrowAndNotProcessWhenPayloadIsMalformed() {
        ConsumerRecord<String, String> malformedRecord = createRecord("{ invalid json syntax ");

        assertThatThrownBy(() -> consumer.consume(malformedRecord))
                .isInstanceOf(MalformedEventException.class)
                .hasMessageContaining("Cannot deserialize MessageAckEvent");

        verifyNoInteractions(acknowledgementPort);
    }

    @Test
    void shouldIgnoreAckWithoutRecipientWithoutReachingTheUseCase() {
        String json = "{\"messageId\":\"" + UUID.randomUUID() + "\"}";

        consumer.consume(createRecord(json)); // does not throw: nothing to retry

        verifyNoInteractions(acknowledgementPort);
    }

    @Test
    void shouldIgnoreAckWithAMalformedMessageId() {
        consumer.consume(createRecord("{\"messageId\":\"not-a-messageId\",\"recipientUsername\":\"bob\"}"));

        verifyNoInteractions(acknowledgementPort);
    }
}
