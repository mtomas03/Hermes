package it.unibo.hermes.worker.application.port.secondary;

import it.unibo.hermes.worker.domain.model.DeliveryStatus;
import it.unibo.hermes.worker.domain.model.Message;
import it.unibo.hermes.worker.domain.model.StoredMessage;

import java.util.Optional;

/**
 * Secondary port interface for storing messages and their delivery status.
 *
 * <p> The implementation owns the single authoritative record of a message
 * and its status and keeps every read model derived from it aligned.
 */
public interface MessageStorePort {

    /**
     * Searches for a message and its authoritative status.
     *
     * @param messageId the message identifier
     * @return the stored message, or empty if the message is unknown
     */
    Optional<StoredMessage> findById(String messageId);

    /**
     * Records the message as {@link DeliveryStatus#PENDING} unless it is already known.
     *
     * <p> If another writer got there first or an earlier attempt already stored it,
     * nothing is overwritten and the status is not reset.
     *
     * @param message the message to record
     * @return the authoritative record: the new one or the one already stored.
     */
    StoredMessage createIfAbsent(Message message);

    /**
     * Makes everything derived from the authoritative record consistent with it: the conversation view of
     * the message, the participants' conversation index and the status shown by the view.
     *
     * <p> Completes the work left over by an earlier attempt that stopped halfway.
     * Does not change the authoritative status and does nothing if the message is unknown.
     *
     * @param messageId the identifier of the message to reconcile
     */
    void reconcileProjection(String messageId);

    /**
     * Moves the status of a message to {@code targetStatus} if and only if {@link DeliveryStatus#canTransition}
     * allows it from the status currently stored, otherwise the stored status is kept.
     *
     * @param messageId    the identifier of the message to update
     * @param targetStatus the status to move the message to
     */
    void advanceStatus(String messageId, DeliveryStatus targetStatus);
}
