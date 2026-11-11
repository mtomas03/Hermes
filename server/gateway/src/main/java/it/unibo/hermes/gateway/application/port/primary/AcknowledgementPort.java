package it.unibo.hermes.gateway.application.port.primary;

/**
 * Primary port interface for recipients confirming that they have received and persisted a message.
 */
public interface AcknowledgementPort {

    /**
     * Records that a recipient has acknowledged a delivered message.
     *
     * @param messageId         the acknowledged message
     * @param recipientUsername the authenticated recipient performing the acknowledgement
     */
    void acknowledge(String messageId, String recipientUsername);
}
