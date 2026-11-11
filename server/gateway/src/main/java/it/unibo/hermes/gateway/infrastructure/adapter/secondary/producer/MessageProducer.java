package it.unibo.hermes.gateway.infrastructure.adapter.secondary.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.application.port.secondary.MessagePublisherPort;
import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.domain.model.Message;
import it.unibo.hermes.gateway.infrastructure.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka producer in Gateway for the {@code message-created} topic:
 * publishes newly created messages to Kafka for further processing.
 */
@Component
public class MessageProducer implements MessagePublisherPort {

    private static final Logger log = LoggerFactory.getLogger(MessageProducer.class);
    private static final long SEND_TIMEOUT_SECONDS = 3L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topic;

    public MessageProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${hermes.topics.message-created:message-created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    @Override
    public void publish(Message message) {
        MessageEvent event = new MessageEvent(
                message.messageId(),
                message.conversationId(),
                message.senderUsername(),
                message.recipientUsername(),
                message.content(),
                message.logicalTimestamp());
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize MessageEvent for message " + event.messageId(), e);
        }

        try {
            kafkaTemplate.send(topic, event.conversationId(), payload)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Published MessageEvent {} to topic {}", event.messageId(), topic);
        } catch (Exception e) {
            throw new BackboneUnavailableException(
                    "Failed to publish message " + event.messageId() + " to Kafka", e);
        }
    }
}