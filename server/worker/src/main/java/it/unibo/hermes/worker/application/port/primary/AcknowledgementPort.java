package it.unibo.hermes.worker.application.port.primary;

import it.unibo.hermes.worker.infrastructure.event.MessageAckEvent;

/**
 * Port interface for processing delivery acknowledgements from Gateway instances.
 */
public interface AcknowledgementPort {

    /**
     * Processes a delivery acknowledgement event.
     *
     * @param event the message acknowledgement event containing the message ID and recipient username
     */
    void processAcknowledgement(MessageAckEvent event);
}
