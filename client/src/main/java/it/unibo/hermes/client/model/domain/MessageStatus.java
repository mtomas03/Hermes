package it.unibo.hermes.client.model.domain;

public enum MessageStatus {
    PENDING,    // composed locally, not yet sent or saved
    SENT,       // sent to the server and saved by server
    FAILED      // could not be sent
}
