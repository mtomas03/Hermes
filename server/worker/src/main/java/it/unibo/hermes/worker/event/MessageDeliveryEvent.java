package it.unibo.hermes.worker.event;

/**
 * Represents an event published by the Delivery Worker when an intended recipient is online
 * and requires real-time message delivery over WebSocket.
 *
 * <p>Events are published to the {@code message-delivery} Kafka topic.
 * Gateway instances consume this topic and filter events whose {@code gatewayId}
 * matches their own identity to forward the payload to the user's active WebSocket session.
 *
 * @param messageId         the unique identifier of the message to deliver (UUID string)
 * @param conversationId    the unique identifier of the target conversation (UUID string)
 * @param senderUsername    the username of the original message sender
 * @param recipientUsername the username of the online target recipient
 * @param gatewayId         the identifier of the gateway instance hosting the recipient's WebSocket session
 * @param content           the plaintext body of the message
 * @param logicalTimestamp  the Lamport logical timestamp used for causal event ordering
 * @param physicalTimestamp the system creation timestamp in epoch milliseconds
 */
public record MessageDeliveryEvent(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String gatewayId,
        String content,
        long logicalTimestamp,
        long physicalTimestamp) {
}