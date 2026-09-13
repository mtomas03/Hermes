package it.unibo.hermes.gateway.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Event published to the Kafka {@code message-created} topic
 * when an inbound client message is accepted.
 *
 * <p>This event is produced by a Gateway upon accepting a new message from a sender's WebSocket session
 * and is consumed by a Worker for double-write Cassandra persistence and delivery processing.
 *
 * @param messageId         the unique identifier generated for the message
 * @param conversationId    the identifier of the conversation to which the message belongs
 * @param senderUsername    the authenticated username of the sender
 * @param recipientUsername the username of the intended recipient
 * @param content           the textual payload of the message
 * @param logicalTimestamp  the logical timestamp assigned for causal message ordering
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageEvent(
        UUID messageId,
        String conversationId,
        String senderUsername,
        String recipientUsername,
        String content,
        Long logicalTimestamp
) {
}