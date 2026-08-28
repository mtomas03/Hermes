package it.unibo.hermes.worker.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unibo.hermes.worker.event.MessageCreatedEvent;
import it.unibo.hermes.worker.event.MessageDeliveryEvent;
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
 * Producer responsible for publishing {@link MessageDeliveryEvent} instances to the Kafka delivery topic.
 *
 * <p> Events are partitioned by setting the record key to {@code gatewayId}, ensuring all messages targeting a specific
 * Gateway instance arrive on the same partition to maintain the order of messages for each gateway.
 * Gateway instances subscribe to this topic to consume and deliver real-time messages to active WebSocket sessions.
 */
@Component
public class DeliveryEventProducer {

    private static final Logger log = LoggerFactory.getLogger(DeliveryEventProducer.class);
    private static final long SEND_TIMEOUT_SECONDS = 5L;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String deliveryTopic;

    /**
     * Creates a new {@code DeliveryEventProducer}.
     *
     * @param kafkaTemplate the template used to send records to Kafka
     * @param objectMapper  the object mapper used to serialize delivery events to JSON
     * @param deliveryTopic the configured Kafka topic name for delivery events
     */
    public DeliveryEventProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${hermes.topics.message-delivery:message-delivery}") String deliveryTopic) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.deliveryTopic = deliveryTopic;
    }

    /**
     * Creates and synchronously publishes a {@link MessageDeliveryEvent} derived from the source creation event,
     * enriched with the destination gateway identifier. The payload is serialized to JSON and published
     * using the target {@code gatewayId} as the record key to guarantee ordered delivery per gateway instance.
     *
     * @param source    the original message creation event containing payload and routing metadata
     * @param gatewayId the identifier of the target gateway instance hosting the recipient's session
     * @throws IllegalStateException if event JSON serialization fails
     * @throws RuntimeException      if the send operation is interrupted, times out, or fails on Kafka
     */
    public void publishDeliveryEvent(MessageCreatedEvent source, String gatewayId) {
        MessageDeliveryEvent deliveryEvent = new MessageDeliveryEvent(
                source.messageId(),
                source.conversationId(),
                source.senderUsername(),
                source.recipientUsername(),
                gatewayId,
                source.content(),
                source.logicalTimestamp(),
                source.physicalTimestamp());

        String payload;
        try {
            payload = objectMapper.writeValueAsString(deliveryEvent);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize MessageDeliveryEvent for message "
                    + source.messageId(), e);
        }

        try {
            // Use gatewayId as the Kafka key to maintain order per gateway.
            SendResult<String, String> result = kafkaTemplate
                    .send(deliveryTopic, gatewayId, payload)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            log.debug("Delivery event published for message {} to gateway {} (partition={}, offset={})",
                    source.messageId(),
                    gatewayId,
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while publishing delivery event for message "
                    + source.messageId(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new RuntimeException("Failed to publish delivery event for message "
                    + source.messageId(), e);
        }
    }
}