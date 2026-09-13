package it.unibo.hermes.gateway.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event published to the Kafka {@code message-acknowledged} topic
 * when a recipient confirms message delivery.
 *
 * <p>This event is produced by a Gateway upon receiving an ACK from the client.
 *
 * @param messageId         the unique identifier of the acknowledged message
 * @param conversationId    the identifier of the conversation to which the message belongs
 * @param senderUsername    the username of the original message sender
 * @param recipientUsername the username of the recipient confirming delivery
 * @param logicalTimestamp  the logical timestamp assigned to the message for causal ordering
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