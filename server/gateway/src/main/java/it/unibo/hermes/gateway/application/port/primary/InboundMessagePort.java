package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.application.model.SendMessageCommand;
import it.unibo.hermes.gateway.domain.exception.PersistenceUnavailableException;
import it.unibo.hermes.gateway.domain.model.Message;

/**
 * Primary port interface for handling inbound messages from clients.
 */
public interface InboundMessagePort {

    /**
     * Publishes an inbound message to the appropriate backend service.
     *
     * @param command the message to send
     * @return the accepted message, used to construct the acknowledgement response
     * @throws PersistenceUnavailableException if the message backbone and the message database are unreachable
     */
    Message publish(SendMessageCommand command);
}
