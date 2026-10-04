package it.unibo.hermes.gateway.consumer;

import it.unibo.hermes.gateway.config.JacksonConfig;
import it.unibo.hermes.gateway.dto.MessageFromGatewayDto;
import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exercises {@link MessageDeliveryConsumer} with the real Gateway {@code ObjectMapper} and with
 * payloads written the way the Worker's {@code MessageDeliveryProducer} writes them: a plain JSON
 * object as the record value, with no Spring Kafka type headers.
 */
@ExtendWith(MockitoExtension.class)
class MessageDeliveryConsumerWireFormatTest {

    private static final String GATEWAY_ID = "gateway-1";
    private static final String TOPIC = "message-delivery";

    @Mock
    private SimpMessagingTemplate messagingTemplate;
    @Mock
    private WebSocketSessionRegistry sessionRegistry;

    private MessageDeliveryConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MessageDeliveryConsumer(
                messagingTemplate, sessionRegistry, new JacksonConfig().objectMapper(), GATEWAY_ID);
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>(TOPIC, 0, 0L, GATEWAY_ID, value);
    }

    @Test
    void deliversEventSerializedByTheWorkerToTheConnectedRecipient() {
        String workerPayload = "{\"messageId\":\"6f1c2f0e-6a52-4b8e-9d52-0f6f6f1d2a11\","
                + "\"conversationId\":\"alice-bob\",\"senderUsername\":\"alice\","
                + "\"recipientUsername\":\"bob\",\"gatewayId\":\"gateway-1\","
                + "\"content\":\"hi\",\"logicalTimestamp\":3}";
        when(sessionRegistry.isConnected("bob")).thenReturn(true);

        consumer.consume(record(workerPayload));

        ArgumentCaptor<MessageFromGatewayDto> payload = ArgumentCaptor.forClass(MessageFromGatewayDto.class);
        verify(messagingTemplate).convertAndSendToUser(eq("bob"), eq("/queue/messages"), payload.capture());
        assertThat(payload.getValue().messageId()).isEqualTo("6f1c2f0e-6a52-4b8e-9d52-0f6f6f1d2a11");
        assertThat(payload.getValue().conversationId()).isEqualTo("alice-bob");
        assertThat(payload.getValue().senderUsername()).isEqualTo("alice");
        assertThat(payload.getValue().content()).isEqualTo("hi");
        assertThat(payload.getValue().logicalTimestamp()).isEqualTo(3L);
    }

    @Test
    void ignoresUnknownFieldsAddedByNewerProducers() {
        String payload = "{\"messageId\":\"m-1\",\"gatewayId\":\"gateway-2\",\"recipientUsername\":\"bob\","
                + "\"someFutureField\":true}";

        consumer.consume(record(payload));

        verifyNoInteractions(sessionRegistry, messagingTemplate);
    }

    @Test
    void rejectsMalformedPayloadWithAnExceptionSoTheErrorHandlerCanSkipIt() {
        assertThatThrownBy(() -> consumer.consume(record("not-json")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cannot deserialize MessageDeliveryEvent");
        verifyNoInteractions(sessionRegistry, messagingTemplate);
    }

    @Test
    void rejectsDoubleEncodedJsonStringPayload() {
        // What a JSON serializer applied to an already-serialized String would put on the wire.
        String doubleEncoded = "\"{\\\"messageId\\\":\\\"m-1\\\",\\\"gatewayId\\\":\\\"gateway-1\\\"}\"";

        assertThatThrownBy(() -> consumer.consume(record(doubleEncoded)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(sessionRegistry, messagingTemplate);
    }
}
