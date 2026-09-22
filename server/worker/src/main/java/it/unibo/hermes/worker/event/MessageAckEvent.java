package it.unibo.hermes.worker.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event produced by the Gateway to the Kafka {@code message-acknowledged} topic
 * when a recipient's client sends an ACK over STOMP, once the message has been received
 * and persisted locally.
 *
 * @param messageId         the unique identifier of the acknowledged message
 * @param recipientUsername the authenticated username of the recipient confirming delivery
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageAckEvent(
        String messageId,
        String recipientUsername
) {}
