package it.unibo.hermes.worker.application.port.primary;

import it.unibo.hermes.worker.infrastructure.event.MessageEvent;

/**
 * Port interface for orchestrating the message delivery pipeline.
 */
public interface DeliveryPort {

    /**
     * Processes a message event by orchestrating the delivery pipeline.
     *
     * @param event the message event to be processed
     */
    void processMessage(MessageEvent event);
}
