package it.unibo.hermes.client.model.domain;

public enum MessageStatus {

    /**
     * Message is composed locally, not yet sent or saved by the server.
     */
    PENDING,

    /**
     * Message is sent to the server and saved in the server message database.
     */
    SENT,

    /**
     * Message has been acknowledged by the client.
     */
    ACKNOWLEDGED,

    /**
     * Message is stored in the server message database.
     */
    STORED,

    /**
     * Message could not be sent.
     */
    FAILED
}
