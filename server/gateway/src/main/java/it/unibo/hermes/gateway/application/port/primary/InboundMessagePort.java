package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.MessageToGatewayDto;
import it.unibo.hermes.gateway.infrastructure.event.MessageEvent;

/**
 * Port interface for handling inbound messages from clients.
 */
public interface InboundMessagePort {

    /**
     * Publishes an inbound message to the appropriate backend service.
     *
     * @param inbound        the inbound message dto containing the message details
     * @param senderUsername the username of the sender
     * @return the accepted message event used to construct the acknowledgement response
     */
    MessageEvent publish(MessageToGatewayDto inbound, String senderUsername);
}
