package it.unibo.hermes.gateway.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event consumed from the Kafka {@code message-delivery} topic
 * to push real-time messages to active clients.
 *
 * <p>This event is produced by a Worker and consumed by a Gateway. The Gateway evaluates
 * the target {@code gatewayId} and routes the message payload over the recipient's active WebSocket session.
 *
 * @param messageId         the unique identifier of the message to deliver
 * @param conversationId    the identifier of the conversation to which the message belongs
 * @param senderUsername    the username of the client who sent the message
 * @param recipientUsername the username of the target recipient
 * @param gatewayId         the identifier of the Gateway instance hosting the recipient's active session
 * @param content           the textual payload of the message
 * @param logicalTimestamp  the logical timestamp for causal message ordering
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageDeliveryEvent(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String gatewayId,
        String content,
        Long logicalTimestamp
) {
}