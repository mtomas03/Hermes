package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.application.model.DeliverMessageCommand;

/**
 * Primary port interface for pushing messages, routed by the backbone, to clients connected to this Gateway.
 */
public interface MessageDeliveryPort {

    /**
     * Delivers a message to its recipient, if the recipient is connected to this Gateway instance.
     *
     * @param command the message and its routing target
     */
    void deliver(DeliverMessageCommand command);
}
