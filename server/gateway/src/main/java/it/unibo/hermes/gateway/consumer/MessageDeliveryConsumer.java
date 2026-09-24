package it.unibo.hermes.gateway.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.domain.MessageStatus;
import it.unibo.hermes.gateway.dto.MessageFromGatewayDto;
import it.unibo.hermes.gateway.event.MessageDeliveryEvent;
import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer in Gateway for the {@code message-delivery} topic.
 */
@Component
public class MessageDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryConsumer.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final String currentGatewayId;

    public MessageDeliveryConsumer(
            SimpMessagingTemplate messagingTemplate,
            WebSocketSessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            @Value("${hermes.gateway.instance-id:gateway-1}") String currentGatewayId) {
        this.messagingTemplate = messagingTemplate;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.currentGatewayId = currentGatewayId;
    }

    @KafkaListener(
            topics = "${hermes.topics.message-delivery:message-delivery}",
            groupId = "${spring.kafka.consumer.group-id:hermes-gateway}-${hermes.gateway.instance-id:gateway-1}",
            containerFactory = "kafkaListenerContainerFactory")
    public void consume(ConsumerRecord<String, String> record) {
        log.debug("Received delivery event record - topic={} partition={} offset={}",
                record.topic(), record.partition(), record.offset());

        MessageDeliveryEvent event = deserialize(record.value());

        if (!currentGatewayId.equals(event.gatewayId())) {
            log.trace("Skipping message {} intended for gateway {}", event.messageId(), event.gatewayId());
            return;
        }

        if (!sessionRegistry.isConnected(event.recipientUsername())) {
            log.warn("Recipient {} not connected on gateway {} - client may have disconnected",
                    event.recipientUsername(), currentGatewayId);
            return;
        }

        log.info("Delivering message {} via STOMP to recipient {}", event.messageId(), event.recipientUsername());

        MessageFromGatewayDto payload = new MessageFromGatewayDto(
                event.messageId(),
                event.conversationId(),
                event.senderUsername(),
                event.recipientUsername(),
                event.content(),
                event.logicalTimestamp(),
                MessageStatus.DELIVERED.name()
        );

        try {
            messagingTemplate.convertAndSendToUser(event.recipientUsername(), "/queue/messages", payload);
            log.debug("Message {} pushed successfully to user {}", event.messageId(), event.recipientUsername());
        } catch (Exception e) {
            log.error("Failed to push message {} to recipient {}: {}",
                    event.messageId(), event.recipientUsername(), e.getMessage());
        }
    }

    private MessageDeliveryEvent deserialize(String json) {
        try {
            return objectMapper.readValue(json, MessageDeliveryEvent.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot deserialize MessageDeliveryEvent: " + json, e);
        }
    }
}
