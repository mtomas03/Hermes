package it.unibo.hermes.gateway.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Event published to the Kafka {@code message-acknowledged} topic when a recipient's client
 * confirms it has received and persisted a message. This event is produced by the Gateway
 * upon receiving a delivery ACK from the client.
 *
 * @param messageId         the unique identifier of the acknowledged message
 * @param recipientUsername the authenticated username of the recipient confirming delivery
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MessageAckEvent(
        String messageId,
        String recipientUsername
) {}
