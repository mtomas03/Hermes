package it.unibo.hermes.worker.domain.model;

import it.unibo.hermes.worker.domain.exception.InvalidAcknowledgementException;

import java.util.UUID;

/**
 * An acknowledgement of a message received by a client, sent back to the worker via the gateway.
 *
 * @param messageId         the identifier of the acknowledged message
 * @param recipientUsername the username of the recipient who received the message
 */
public record Acknowledgement(String messageId, String recipientUsername) {

    public Acknowledgement {
        if (messageId == null || !isUUID(messageId)) {
            throw new InvalidAcknowledgementException("messageId is missing or is not a valid UUID");
        }
        if (recipientUsername == null || recipientUsername.isBlank()) {
            throw new InvalidAcknowledgementException("Acknowledgement of message " + messageId
                    + ": recipientUsername is required");
        }
    }

    private boolean isUUID(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
