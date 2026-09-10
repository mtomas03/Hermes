package it.unibo.hermes.gateway.domain;

/**
 * Lifecycle states of a message as it moves through the system.
 * State transitions are monotonic where a message never moves backward.
 */
public enum MessageStatus {

    /**
     * Message has been accepted from Kafka but the delivery decision
     * has not yet been made or completed.
     */
    PENDING,

    /**
     * Recipient is offline; message is durably stored in Cassandra
     * and will be retrieved during the next synchronisation pull.
     */
    STORED,

    /**
     * Delivery event has been forwarded to the target gateway.
     * Waiting for the WebSocket push and subsequent acknowledgement.
     */
    DELIVERING,

    /**
     * Gateway pushed the message to the recipient's WebSocket.
     * Waiting for explicit client acknowledgement.
     */
    DELIVERED,

    /**
     * Recipient has explicitly acknowledged receipt.
     */
    ACKNOWLEDGED
}