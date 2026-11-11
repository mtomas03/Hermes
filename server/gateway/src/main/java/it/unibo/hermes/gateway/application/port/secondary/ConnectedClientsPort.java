package it.unibo.hermes.gateway.application.port.secondary;

import java.util.Set;

/**
 * Secondary port interface for managing the set of clients currently connected to this Gateway instance.
 */
public interface ConnectedClientsPort {

    /**
     * Registers a client as connected to this Gateway instance.
     *
     * @param username the username of the client to register
     */
    void register(String username);

    /**
     * Unregisters a client as connected to this Gateway instance.
     *
     * @param username the username of the client to unregister
     */
    void unregister(String username);

    /**
     * Checks if a client is currently connected to this Gateway instance.
     *
     * @param username the username of the client to check
     * @return true if the client is connected, false otherwise
     */
    boolean isConnected(String username);

    /**
     * Returns the set of usernames of all clients currently connected to this Gateway instance.
     *
     * @return a set of usernames of connected clients
     */
    Set<String> allUsernames();
}
