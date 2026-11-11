package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.primary.AcknowledgementPort;
import it.unibo.hermes.gateway.application.port.secondary.AckPublisherPort;
import org.springframework.stereotype.Service;

/**
 * Service forwarding recipient acknowledgements to the message backbone.
 */
@Service
public class AcknowledgementService implements AcknowledgementPort {

    private final AckPublisherPort ackPublisher;

    /**
     * Creates the acknowledgement service.
     *
     * @param ackPublisher the port publishing acknowledgements to the backbone
     */
    public AcknowledgementService(AckPublisherPort ackPublisher) {
        this.ackPublisher = ackPublisher;
    }

    @Override
    public void acknowledge(String messageId, String recipientUsername) {
        ackPublisher.publishAck(messageId, recipientUsername);
    }
}
