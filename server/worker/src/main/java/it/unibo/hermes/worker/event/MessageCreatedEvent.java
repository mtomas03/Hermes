package it.unibo.hermes.worker.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an event sent by the Gateway when a user sends a new message.
 *
 * <p>Messages of this type are consumed from the {@code message-created} Kafka topic.
 *
 * @param messageId         the unique identifier of the message (UUID string)
 * @param conversationId    the identifier of the target conversation (UUID string)
 * @param senderUsername    the username of the user sending the message
 * @param recipientUsername the username of the target recipient
 * @param content           the plaintext body of the message
 * @param logicalTimestamp  the Lamport logical timestamp used for causal event ordering
 * @param physicalTimestamp the system creation timestamp in epoch milliseconds
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageCreatedEvent(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        long logicalTimestamp,
        long physicalTimestamp) {
}