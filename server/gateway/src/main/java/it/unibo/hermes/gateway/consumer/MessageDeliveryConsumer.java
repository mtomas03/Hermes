package it.unibo.hermes.gateway.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.gateway.event.MessageDeliveryEvent;
import it.unibo.hermes.gateway.websocket.WebSocketSessionRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Optional;

/**
 * Kafka consumer in Gateway for the {@code message-delivery} topic.
 */
@Component
public class MessageDeliveryConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryConsumer.class);

    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final String currentGatewayId;

    public MessageDeliveryConsumer(
            WebSocketSessionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            @Value("${hermes.gateway.instance-id:gateway-1}") String currentGatewayId) {
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

        // Process only if the message is targeted at THIS gateway instance
        if (!currentGatewayId.equals(event.gatewayId())) {
            log.trace("Skipping message {} intended for gateway {}", event.messageId(), event.gatewayId());
            return;
        }

        log.info("Delivering message {} via WebSocket to recipient {}", event.messageId(), event.recipientUsername());

        Optional<WebSocketSession> sessionOpt = sessionRegistry.sessionOf(event.recipientUsername());

        if (sessionOpt.isPresent() && sessionOpt.get().isOpen()) {
            WebSocketSession session = sessionOpt.get();
            try {
                String payload = objectMapper.writeValueAsString(event);
                synchronized (session) {
                    session.sendMessage(new TextMessage(payload));
                }
                log.debug("Message {} pushed successfully to user {}", event.messageId(), event.recipientUsername());
            } catch (Exception e) {
                log.error("Failed to push message {} to recipient WebSocket {}: {}",
                        event.messageId(), event.recipientUsername(), e.getMessage());
            }
        } else {
            log.warn("Recipient {} session not active on gateway {} - client may have disconnected",
                    event.recipientUsername(), currentGatewayId);
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
