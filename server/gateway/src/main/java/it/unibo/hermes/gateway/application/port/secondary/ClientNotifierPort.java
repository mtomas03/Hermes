package it.unibo.hermes.gateway.application.port.secondary;

import it.unibo.hermes.gateway.domain.model.Message;

/**
 * Secondary port interface for pushing information to clients connected to this Gateway instance.
 */
public interface ClientNotifierPort {

    /**
     * Pushes a message to its recipient.
     *
     * @param message the message to push
     */
    void pushMessage(Message message);

    /**
     * Asks a client to reconnect and re-synchronise.
     *
     * @param username the username of the client to ask for reconnection
     * @param reason   the reason for the reconnection request
     */
    void requestReconnect(String username, String reason);
}
