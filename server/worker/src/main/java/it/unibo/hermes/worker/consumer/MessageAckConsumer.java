package it.unibo.hermes.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.event.MessageAckEvent;
import it.unibo.hermes.worker.service.AcknowledgementService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the {@code message-acknowledged} topic.
 *
 * <p> Records on this topic are produced by the Gateway after the recipient's
 * WebSocket session sends an explicit delivery confirmation. The consumer
 * delegates to {@link AcknowledgementService} to advance the message state
 * to {@link DeliveryStatus#ACKNOWLEDGED}.
 */
@Component
public class MessageAckConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageAckConsumer.class);

    private final AcknowledgementService acknowledgementService;
    private final ObjectMapper objectMapper;

    public MessageAckConsumer(
            AcknowledgementService acknowledgementService,
            ObjectMapper objectMapper) {
        this.acknowledgementService = acknowledgementService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${hermes.topics.message-acknowledged:message-acknowledged}",
            groupId = "${spring.kafka.consumer.group-id:hermes-delivery-worker}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        log.debug("Received message-acknowledged event - topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());

        MessageAckEvent event = deserialize(record.value());
        log.info("Processing MessageAckEvent - messageId={} recipient={}",
                event.messageId(), event.recipientUsername());

        acknowledgementService.processAcknowledgement(event);
    }

    private MessageAckEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, MessageAckEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize MessageAckEvent: " + json, e);
        }
    }
}
