package it.unibo.hermes.worker.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents an event sent by the Gateway when a user sends a new message.
 *
 * <p>Messages of this type are consumed from the {@code message-created} Kafka topic.
 *
 * @param messageId         the unique identifier of the message
 * @param conversationId    the identifier of the target conversation
 * @param senderUsername    the username of the user sending the message
 * @param recipientUsername the username of the target recipient
 * @param content           the plaintext body of the message
 * @param logicalTimestamp  the Lamport logical timestamp used for causal event ordering
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageEvent(
        String messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp
) {
}