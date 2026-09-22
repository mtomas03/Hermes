package it.unibo.hermes.client.exception;

/**
 * Thrown when an inbound message cannot be durably persisted to the local SQLite database.
 *
 * <p> No acknowledgement should be sent back to the Gateway when this exception is raised.
 */
public class MessagePersistenceException extends RuntimeException {

    /**
     * Creates a new {@code MessagePersistenceException} for the given message.
     *
     * @param messageId     the identifier of the message that could not be persisted
     */
    public MessagePersistenceException(String messageId) {
        super("Failed to durably persist the message " + messageId + " locally to the SQLite database.");
    }
}
