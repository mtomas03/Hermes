package it.unibo.hermes.gateway.application.port.secondary;

import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;

import java.time.Duration;

/**
 * Secondary port interface for the publication of recipient acknowledgements to the message backbone.
 */
public interface AckPublisherPort {

    /**
     * Publishes an acknowledgement for a message to the message backbone
     * and waits for the broker's confirmation for at most {@code timeout}.
     *
     * @param messageId         the ID of the message being acknowledged
     * @param recipientUsername the username of the recipient acknowledging the message
     * @param timeout           the max time to wait for the broker's confirmation
     * @throws BackboneUnavailableException if the broker did not confirm the acknowledgement
     */
    void publishAck(String messageId, String recipientUsername, Duration timeout);
}
