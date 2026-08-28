package it.unibo.hermes.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.config.KafkaConfig;
import it.unibo.hermes.worker.event.MessageCreatedEvent;
import it.unibo.hermes.worker.service.DeliveryService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the {@code message-created} topic.
 *
 * <p> Each record corresponds to a message that was accepted and forwarded by
 * the Gateway to Kafka. This consumer deserializes the JSON
 * payload and hands it off to {@link DeliveryService} for processing.
 *
 * <p> Error handling and bounded retry are configured globally in
 * {@link KafkaConfig}. If all retries are exhausted,
 * the record is logged and skipped.
 */
@Component
public class MessageCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageCreatedConsumer.class);

    private final DeliveryService deliveryService;
    private final ObjectMapper objectMapper;

    public MessageCreatedConsumer(DeliveryService deliveryService, ObjectMapper objectMapper) {
        this.deliveryService = deliveryService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${hermes.topics.message-created:message-created}",
            groupId = "${spring.kafka.consumer.group-id:hermes-delivery-worker}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        log.debug("Received message-created event - topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());

        MessageCreatedEvent event = deserialize(record.value());
        log.info("Processing MessageCreatedEvent - messageId={} sender={} recipient={}",
                event.messageId(), event.senderUsername(), event.recipientUsername());

        deliveryService.processMessage(event);
    }

    private MessageCreatedEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, MessageCreatedEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize MessageCreatedEvent: " + json, e);
        }
    }
}
