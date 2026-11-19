package it.unibo.hermes.worker.infrastructure.adapter.secondary.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.application.exception.DeliveryPublicationException;
import it.unibo.hermes.worker.application.port.secondary.MessageDeliveryProducerPort;
import it.unibo.hermes.worker.domain.model.Message;
import it.unibo.hermes.worker.infrastructure.event.MessageDeliveryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Kafka producer in Worker for the {@code message-delivery} topic:
 * publishes messages to be delivered to the recipient's gateway for real-time WebSocket delivery.
 */
@Component
public class MessageDeliveryProducer implements MessageDeliveryProducerPort {

    private static final Logger log = LoggerFactory.getLogger(MessageDeliveryProducer.class);
    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String deliveryTopic;

    /**
     * Creates a new {@code MessageDeliveryProducer}.
     *
     * @param kafkaTemplate the template used to send records to Kafka
     * @param objectMapper  the object mapper used to serialize delivery events to JSON
     * @param deliveryTopic the configured Kafka topic name for delivery events
     */
    public MessageDeliveryProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${hermes.topics.message-delivery:message-delivery}") String deliveryTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.deliveryTopic = deliveryTopic;
    }

    /**
     * Creates and synchronously publishes a {@link MessageDeliveryEvent},
     * enriched with the destination gateway identifier. The payload is serialized to JSON and published
     * using the {@code gatewayId} as the record key to guarantee ordered delivery per gateway instance.
     *
     * @param message   the message to deliver
     * @param gatewayId the identifier of the target gateway connected to the recipient
     * @throws DeliveryPublicationException if serialization fails, or the send is interrupted, times out
     *                                      or fails on Kafka
     */
    @Override
    public void publish(Message message, String gatewayId) {
        MessageDeliveryEvent deliveryEvent = new MessageDeliveryEvent(
                message.messageId(),
                message.conversationId(),
                message.senderUsername(),
                message.recipientUsername(),
                gatewayId,
                message.content(),
                message.logicalTimestamp()
        );

        String payload;
        try {
            payload = objectMapper.writeValueAsString(deliveryEvent);
        } catch (JsonProcessingException e) {
            throw new DeliveryPublicationException("Failed to serialize MessageDeliveryEvent for message "
                    + message.messageId(), e);
        }

        try {
            // Use gatewayId as the Kafka key to maintain order per gateway.
            SendResult<String, String> result = kafkaTemplate
                    .send(deliveryTopic, gatewayId, payload)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.debug("Delivery event published for message {} to gateway {} (partition={}, offset={})",
                    message.messageId(),
                    gatewayId,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DeliveryPublicationException("Interrupted while publishing delivery event for message "
                    + message.messageId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new DeliveryPublicationException("Failed to publish delivery event for message "
                    + message.messageId(), e);
        }
    }
}
