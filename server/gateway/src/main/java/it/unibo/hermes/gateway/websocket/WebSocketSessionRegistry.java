package it.unibo.hermes.gateway.websocket;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry tracking the usernames currently connected to this Gateway instance over STOMP.
 */
@Component
public class WebSocketSessionRegistry {

    private final Set<String> connectedUsernames = ConcurrentHashMap.newKeySet();

    /**
     * Marks a username as connected on this Gateway instance.
     *
     * @param username the username associated with the newly established STOMP session
     */
    public void register(String username) {
        connectedUsernames.add(username);
    }

    /**
     * Removes a username from the set of currently connected users.
     *
     * @param username the username to unregister
     */
    public void unregister(String username) {
        connectedUsernames.remove(username);
    }

    /**
     * Tells whether the given user currently has an active STOMP session on this instance.
     *
     * @param username the target username
     * @return {@code true} if the user is currently connected, {@code false} otherwise
     */
    public boolean isConnected(String username) {
        return connectedUsernames.contains(username);
    }

    /**
     * Creates a snapshot of all usernames currently connected to this Gateway instance.
     *
     * @return an immutable snapshot of the connected usernames
     */
    public Set<String> allUsernames() {
        return Set.copyOf(connectedUsernames);
    }

    /**
     * Returns the total count of currently connected users on this Gateway instance.
     *
     * @return the number of connected users
     */
    public int size() {
        return connectedUsernames.size();
    }
}
