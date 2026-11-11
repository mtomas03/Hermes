package it.unibo.hermes.gateway.application.port.secondary;

/**
 * Secondary port interface for the publication of recipient acknowledgements to the message backbone.
 */
public interface AckPublisherPort {

    /**
     * Publishes an acknowledgement for a message to the message backbone.
     *
     * @param messageId         the ID of the message being acknowledged
     * @param recipientUsername the username of the recipient acknowledging the message
     */
    void publishAck(String messageId, String recipientUsername);
}
