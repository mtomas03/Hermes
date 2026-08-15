package it.unibo.hermes.worker.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an event sent by the Gateway when a recipient's WebSocket session
 * sends an explicit delivery acknowledgement.
 *
 * <p>Events of this type are consumed from the {@code message-acknowledged} Kafka topic.
 *
 * @param messageId         the unique identifier of the acknowledged message (UUID string)
 * @param conversationId    the unique identifier of the target conversation (UUID string)
 * @param recipientUsername the username of the recipient confirming message delivery
 * @param acknowledgedAt    the system timestamp when the acknowledgement was received by the Gateway, in epoch milliseconds
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageAcknowledgedEvent(
        String messageId,
        String conversationId,
        String recipientUsername,
        long acknowledgedAt) {
}