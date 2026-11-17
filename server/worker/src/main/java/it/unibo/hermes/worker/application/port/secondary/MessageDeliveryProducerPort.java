package it.unibo.hermes.worker.application.port.secondary;

import it.unibo.hermes.worker.application.exception.DeliveryPublicationException;
import it.unibo.hermes.worker.domain.model.Message;

/**
 * Secondary port interface for handing a message over to the gateway
 * that holds the recipient's live session, which pushes it to the recipient.
 */
public interface MessageDeliveryProducerPort {

    /**
     * Publishes the delivery request and waits until it is confirmed.
     *
     * @param message   the message to deliver
     * @param gatewayId the identifier of the Gateway instance that holds the recipient's session
     * @throws DeliveryPublicationException if the request could not be published or confirmed
     */
    void publish(Message message, String gatewayId);
}
