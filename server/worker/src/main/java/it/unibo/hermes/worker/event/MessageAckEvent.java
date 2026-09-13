package it.unibo.hermes.worker.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an event produced by the Gateway when a recipient's WebSocket session
 * sends an explicit delivery acknowledgement.
 *
 * <p>Events of this type are consumed from the {@code message-acknowledged} Kafka topic.
 *
 * @param messageId         the unique identifier of the acknowledged message
 * @param conversationId    the unique identifier of the target conversation
 * @param senderUsername    the username of the message sender
 * @param recipientUsername the username of the recipient confirming message delivery
 * @param logicalTimestamp  the logical timestamp assigned for causal message ordering
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageAckEvent(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        Long logicalTimestamp
) {
}