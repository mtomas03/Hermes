package it.unibo.hermes.worker.application.port.primary;

import it.unibo.hermes.worker.application.exception.PersistenceUnavailableException;
import it.unibo.hermes.worker.domain.exception.MessageConflictException;
import it.unibo.hermes.worker.domain.model.Message;

/**
 * Primary port interface for taking charge of a message accepted by the Gateway,
 * persist it and route it to its recipient.
 */
public interface DeliveryPort {

    /**
     * Persists the message and decides how it reaches the recipient.
     *
     * @param message the message to process
     * @throws MessageConflictException        if the identifier is already used by a different message
     * @throws PersistenceUnavailableException if the message could not be persisted or its status updated
     */
    void processMessage(Message message);
}
