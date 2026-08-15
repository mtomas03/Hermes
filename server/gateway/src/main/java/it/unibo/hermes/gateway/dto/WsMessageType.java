package it.unibo.hermes.gateway.dto;

/**
 * List of all message types exchanged over the WebSocket channel.
 */
public enum WsMessageType {
    /**
     * Message submitted by a client to send a new message to a recipient.
     */
    SEND_MESSAGE,

    /**
     * Message submitted by a client to acknowledge receipt of a delivered message.
     */
    ACK,

    /**
     * Heartbeat probe message initiated by the client to test connectivity.
     */
    PING,

    /**
     * Message pushed by the Gateway to deliver an incoming message to an active recipient session.
     */
    RECEIVE_MESSAGE,

    /**
     * Message dispatched by the Gateway confirming processing acceptance of a SEND_MESSAGE request.
     */
    MESSAGE_ACCEPTED,

    /**
     * Heartbeat response message returned by the Gateway in response to a PING message.
     */
    PONG,

    /**
     * Error notification message dispatched when message processing encounters a failure.
     */
    ERROR,

    /**
     * Control message sent by the Gateway instructing the client to immediately close
     * and re-establish its WebSocket connection after Kafka has been recovered.
     */
    FORCE_RECONNECT
}