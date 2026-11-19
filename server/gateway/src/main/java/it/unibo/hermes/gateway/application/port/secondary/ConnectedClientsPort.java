package it.unibo.hermes.gateway.application.port.secondary;

import java.util.Set;

/**
 * Secondary port interface for managing the set of clients currently connected to this Gateway instance.
 */
public interface ConnectedClientsPort {

    /**
     * Records a STOMP session of a user.
     *
     * <p> A user may hold several sessions at once.
     *
     * @param username  the authenticated user
     * @param sessionId the STOMP session identifier
     */
    void register(String username, String sessionId);

    /**
     * Removes one STOMP session of a user.
     *
     * @param username  the authenticated user
     * @param sessionId the STOMP session identifier
     * @return true only if this call removed the user's last open session
     */
    boolean unregister(String username, String sessionId);

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
