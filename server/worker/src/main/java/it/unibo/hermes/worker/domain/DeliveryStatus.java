package it.unibo.hermes.worker.domain;

/**
 * Lifecycle states of a message as tracked by the Delivery Worker.
 * State transitions are monotone: a message never moves backward.
 */
public enum DeliveryStatus {

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
     * Awaiting explicit client acknowledgement.
     */
    DELIVERED,

    /**
     * Recipient has explicitly acknowledged receipt.
     */
    ACKNOWLEDGED
}
