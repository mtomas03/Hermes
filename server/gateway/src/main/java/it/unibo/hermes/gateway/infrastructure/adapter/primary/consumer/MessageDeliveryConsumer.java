package it.unibo.hermes.gateway.infrastructure.adapter.primary.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.application.model.DeliverMessageCommand;
import it.unibo.hermes.gateway.application.port.primary.MessageDeliveryPort;
import it.unibo.hermes.gateway.infrastructure.event.MessageDeliveryEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer in Gateway for the {@code message-delivery} topic:
 * consumes delivery events and forwards them to the delivery port.
 */
@Component
public class MessageDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryConsumer.class);

    private final MessageDeliveryPort messageDeliveryPort;
    private final ObjectMapper objectMapper;

    /**
     * Creates the delivery consumer.
     *
     * @param messageDeliveryPort the primary port delivering messages to connected clients
     * @param objectMapper        the mapper deserialising delivery events
     */
    public MessageDeliveryConsumer(MessageDeliveryPort messageDeliveryPort, ObjectMapper objectMapper) {
        this.messageDeliveryPort = messageDeliveryPort;
        this.objectMapper = objectMapper;
    }

    /**
     * Consumes a delivery event and forwards it to the delivery port.
     *
     * @param record the Kafka record carrying a serialised {@link MessageDeliveryEvent}
     * @throws IllegalArgumentException if the event cannot be deserialised into a {@link MessageDeliveryEvent}
     */
    @KafkaListener(
            topics = "${hermes.topics.message-delivery:message-delivery}",
            groupId = "${spring.kafka.consumer.group-id:hermes-gateway}-${hermes.gateway.instance-id:gateway-1}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        log.debug("Received delivery event record - topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());

        MessageDeliveryEvent event = deserialize(record.value());

        messageDeliveryPort.deliver(new DeliverMessageCommand(
                event.messageId(),
                event.conversationId(),
                event.senderUsername(),
                event.recipientUsername(),
                event.gatewayId(),
                event.content(),
                event.logicalTimestamp()));
    }

    private MessageDeliveryEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, MessageDeliveryEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize MessageDeliveryEvent: " + json, e);
        }
    }
}
