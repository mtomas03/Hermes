package it.unibo.hermes.gateway.infrastructure.adapter.secondary.session;

import it.unibo.hermes.gateway.application.port.secondary.ConnectedClientsPort;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secondary adapter registry implementing {@link ConnectedClientsPort}
 * by, per username, the STOMP sessions currently open on this Gateway instance.
 */
@Component
public class WebSocketSessionRegistry implements ConnectedClientsPort {

    private final Map<String, Set<String>> sessionsByUsername = new ConcurrentHashMap<>();

    @Override
    public void register(String username, String sessionId) {
        sessionsByUsername.compute(username, (user, sessions) -> {
            Set<String> updated = sessions == null ? new HashSet<>() : sessions;
            updated.add(sessionId);
            return updated;
        });
    }

    @Override
    public boolean unregister(String username, String sessionId) {
        boolean[] lastRemoved = {false};
        sessionsByUsername.computeIfPresent(username, (user, sessions) -> {
            if (!sessions.remove(sessionId)) {
                return sessions;
            }
            if (sessions.isEmpty()) {
                lastRemoved[0] = true;
                return null;
            }
            return sessions;
        });
        return lastRemoved[0];
    }

    @Override
    public boolean isConnected(String username) {
        return sessionsByUsername.containsKey(username);
    }

    @Override
    public Set<String> allUsernames() {
        return Set.copyOf(sessionsByUsername.keySet());
    }

    /**
     * Returns the total count of currently connected
     * distinct users on this Gateway instance.
     *
     * @return the number of connected users
     */
    public int size() {
        return sessionsByUsername.size();
    }
}
