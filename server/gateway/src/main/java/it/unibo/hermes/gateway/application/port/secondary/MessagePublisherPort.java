package it.unibo.hermes.gateway.application.port.secondary;

import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import it.unibo.hermes.gateway.domain.model.Message;

/**
 * Secondary port interface for publication of newly accepted messages to the message backbone.
 */
public interface MessagePublisherPort {

    /**
     * Publishes a message to the message backbone.
     *
     * @param message the accepted message
     * @throws BackboneUnavailableException if publication fails
     */
    void publish(Message message);
}
