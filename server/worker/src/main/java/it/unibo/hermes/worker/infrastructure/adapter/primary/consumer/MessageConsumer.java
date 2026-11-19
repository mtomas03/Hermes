package it.unibo.hermes.worker.infrastructure.adapter.primary.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.application.port.primary.DeliveryPort;
import it.unibo.hermes.worker.infrastructure.adapter.primary.mapper.MessageEventMapper;
import it.unibo.hermes.worker.infrastructure.event.MessageEvent;
import it.unibo.hermes.worker.infrastructure.adapter.primary.exception.MalformedEventException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the {@code message-created} topic:
 * receives events when a new message is created and triggers the delivery process.
 */
@Component
public class MessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);

    private final DeliveryPort deliveryPort;
    private final ObjectMapper objectMapper;

    public MessageConsumer(DeliveryPort deliveryPort, ObjectMapper objectMapper) {
        this.deliveryPort = deliveryPort;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "${hermes.topics.message-created:message-created}",
            groupId = "${spring.kafka.consumer.group-id:hermes-delivery-worker}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        log.debug("Received message-created event - topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());

        MessageEvent event = deserialize(record);
        log.info("Processing MessageEvent - messageId={} sender={} recipient={}",
                event.messageId(), event.senderUsername(), event.recipientUsername());

        deliveryPort.processMessage(MessageEventMapper.toDomain(event));
    }

    private MessageEvent deserialize(ConsumerRecord<String, String> record) {
        try {
            return objectMapper.readValue(record.value(), MessageEvent.class);
        } catch (Exception e) {
            throw new MalformedEventException("MessageEvent",
                    record.topic(), record.partition(), record.offset(),
                    e.getClass().getSimpleName());
        }
    }
}
