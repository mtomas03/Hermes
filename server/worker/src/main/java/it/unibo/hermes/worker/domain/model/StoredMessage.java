package it.unibo.hermes.worker.domain.model;

/**
 * Represents a message that has been stored in the message store database,
 * along with its delivery status.
 *
 * @param message the message data
 * @param status  the authoritative delivery status
 */
public record StoredMessage(Message message, DeliveryStatus status) {

    /**
     * A message is processed as soon as its delivery has moved past {@link DeliveryStatus#PENDING}:
     * the routing decision has been taken or the message has been stored by another path.
     *
     * @return {@code true} if the status is anything other than {@code PENDING}
     */
    public boolean isProcessed() {
        return status != DeliveryStatus.PENDING;
    }
}
