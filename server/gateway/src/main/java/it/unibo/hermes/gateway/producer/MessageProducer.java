package it.unibo.hermes.gateway.producer;

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

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;

    public MessageProducer(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${hermes.topics.message-created:message-created}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    /**
     * Synchronously publishes a newly created message event, partitioned by conversation ID.
     *
     * @param event the message event to publish
     * @throws BackboneUnavailableException if the send times out or Kafka is unreachable
     */
    public void publish(MessageEvent event) {
        try {
            kafkaTemplate.send(topic, event.conversationId(), event)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.debug("Published MessageEvent {} to topic {}", event.messageId(), topic);
        } catch (Exception e) {
            throw new BackboneUnavailableException(
                    "Failed to publish message " + event.messageId() + " to Kafka", e);
        }
    }
}