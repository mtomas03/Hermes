package it.unibo.hermes.gateway.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.BackboneUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Producer responsible for publishing newly created message events to Kafka.
 */
@Component
public class MessageProducer {

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

    /**
     * Synchronously publishes a newly created message event as a JSON string,
     * partitioned by conversation ID (the record key).
     *
     * @param event the message event to publish
     * @throws IllegalStateException        if the event cannot be serialized to JSON
     * @throws BackboneUnavailableException if the send times out or Kafka is unreachable
     */
    public void publish(MessageEvent event) {
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