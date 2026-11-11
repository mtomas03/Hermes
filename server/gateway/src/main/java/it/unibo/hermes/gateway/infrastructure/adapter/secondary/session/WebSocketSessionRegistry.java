package it.unibo.hermes.gateway.infrastructure.adapter.secondary.session;

import it.unibo.hermes.gateway.application.port.secondary.ConnectedClientsPort;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secondary adapter registry implementing {@link ConnectedClientsPort}
 * by tracking the usernames currently connected to this Gateway instance over STOMP.
 */
@Component
public class WebSocketSessionRegistry implements ConnectedClientsPort {

    private final Set<String> connectedUsernames = ConcurrentHashMap.newKeySet();

    @Override
    public void register(String username) {
        connectedUsernames.add(username);
    }

    @Override
    public void unregister(String username) {
        connectedUsernames.remove(username);
    }

    @Override
    public boolean isConnected(String username) {
        return connectedUsernames.contains(username);
    }

    @Override
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
