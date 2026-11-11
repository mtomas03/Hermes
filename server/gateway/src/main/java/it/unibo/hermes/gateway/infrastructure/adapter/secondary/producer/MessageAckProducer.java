package it.unibo.hermes.gateway.infrastructure.adapter.secondary.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.application.port.secondary.AckPublisherPort;
import it.unibo.hermes.gateway.infrastructure.event.MessageAckEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka producer in Gateway for the {@code message-acknowledged} topic:
 * publishes ACK events when a message is acknowledged by a client.
 */
@Component
public class MessageAckProducer implements AckPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(MessageAckProducer.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String ackTopic;

    public MessageAckProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${hermes.topics.message-acknowledged:message-acknowledged}") String ackTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.ackTopic = ackTopic;
    }

    @Override
    public void publishAck(String messageId, String recipientUsername) {
        MessageAckEvent ackEvent = new MessageAckEvent(messageId, recipientUsername);
        try {
            String payload = objectMapper.writeValueAsString(ackEvent);
            kafkaTemplate.send(ackTopic, ackEvent.messageId(), payload);
            log.info("Published MessageAckEvent for message {} from recipient {}",
                    ackEvent.messageId(), ackEvent.recipientUsername());
        } catch (Exception e) {
            log.error("Failed to publish ACK event for message {}: {}",
                    ackEvent.messageId(), e.getMessage());
        }
    }
}