package it.unibo.hermes.worker.application.port.primary;

import it.unibo.hermes.worker.application.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.domain.model.Acknowledgement;

/**
 * Primary port interface for recording that a recipient has acknowledged the receipt of a message.
 */
public interface AcknowledgementPort {

    /**
     * Moves the acknowledged message to {@code ACKNOWLEDGED}.
     *
     * @param acknowledgement the acknowledgement to process
     * @throws PersistenceUnavailableException if the status could not be updated
     */
    void processAcknowledgement(Acknowledgement acknowledgement);
}
