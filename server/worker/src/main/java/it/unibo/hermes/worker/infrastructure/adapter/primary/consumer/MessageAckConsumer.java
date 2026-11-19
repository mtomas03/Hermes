package it.unibo.hermes.worker.infrastructure.adapter.primary.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.application.port.primary.AcknowledgementPort;
import it.unibo.hermes.worker.domain.exception.InvalidAcknowledgementException;
import it.unibo.hermes.worker.infrastructure.adapter.primary.mapper.AcknowledgementEventMapper;
import it.unibo.hermes.worker.infrastructure.event.MessageAckEvent;
import it.unibo.hermes.worker.infrastructure.adapter.primary.exception.MalformedEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the {@code message-acknowledged} topic:
 * receives and processes message acknowledgements.
 */
@Component
public class MessageAckConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageAckConsumer.class);

    private final AcknowledgementPort acknowledgementPort;
    private final ObjectMapper objectMapper;

    public MessageAckConsumer(
            AcknowledgementPort acknowledgementPort,
            ObjectMapper objectMapper) {
        this.acknowledgementPort = acknowledgementPort;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${hermes.topics.message-acknowledged:message-acknowledged}",
            groupId = "${spring.kafka.consumer.group-id:hermes-delivery-worker}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        log.debug("Received message-acknowledged event - topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());

        MessageAckEvent event = deserialize(record);
        log.info("Processing MessageAckEvent - messageId={} recipient={}",
                event.messageId(), event.recipientUsername());

        try {
            acknowledgementPort.processAcknowledgement(AcknowledgementEventMapper.toDomain(event));
        } catch (InvalidAcknowledgementException e) {
            log.warn("Ignoring invalid ACK: {}", e.getMessage());
        }
    }

    private MessageAckEvent deserialize(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readValue(record.value(), MessageAckEvent.class);
        } catch (Exception e) {
            throw new MalformedEventException("MessageAckEvent",
                    record.topic(), record.partition(), record.offset(),
                    e.getClass().getSimpleName());
        }
    }
}
