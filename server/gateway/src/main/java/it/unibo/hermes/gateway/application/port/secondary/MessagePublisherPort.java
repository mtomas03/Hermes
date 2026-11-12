package it.unibo.hermes.gateway.application.port.secondary;

import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.domain.model.Message;

import java.time.Duration;

/**
 * Secondary port interface for publication of newly accepted messages to the message backbone.
 */
public interface MessagePublisherPort {

    /**
     * Publishes a message to the message backbone
     * and waits for the broker's acknowledgement for at most {@code timeout}.
     *
     * @param message the accepted message
     * @param timeout the max time to wait for the broker's acknowledgement
     * @throws BackboneUnavailableException if publication fails; its {@code isOutcomeUnknown()} tells a timeout
     *                                      (record possibly published) from a confirmed failure
     */
    void publish(Message message, Duration timeout);
}
