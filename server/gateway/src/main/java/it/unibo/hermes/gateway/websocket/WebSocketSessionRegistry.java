package it.unibo.hermes.gateway.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry tracking active WebSocket sessions and client heartbeat timestamps.
 */
@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    /**
     * Binds a username to an active WebSocket session and initialises its heartbeat timestamp.
     *
     * @param username the username associated with the session
     * @param session  the active WebSocket session instance
     */
    public void register(String username, WebSocketSession session) {
        sessions.put(username, new SessionEntry(session, Instant.now()));
    }

    /**
     * Removes the specified user's session from the active registry.
     *
     * @param username the username to unregister
     */
    public void unregister(String username) {
        sessions.remove(username);
    }

    /**
     * Updates the last received heartbeat timestamp for the specified user to current time.
     *
     * @param username the username of the pinging client
     */
    public void recordHeartbeat(String username) {
        sessions.computeIfPresent(username,
                (k, e) -> new SessionEntry(e.session(), Instant.now()));
    }

    /**
     * Retrieves the active WebSocket session assigned to the specified user.
     *
     * @param username the target username
     * @return an {@link Optional} containing the session if registered, or empty if absent
     */
    public Optional<WebSocketSession> sessionOf(String username) {
        return Optional.ofNullable(sessions.get(username)).map(SessionEntry::session);
    }

    /**
     * Creates a snapshot collection containing all currently registered session entries.
     *
     * @return a collection of active registry entries
     */
    public Collection<Entry> allEntries() {
        return sessions.entrySet().stream()
                .map(e -> new Entry(e.getKey(), e.getValue().session(), e.getValue().lastHeartbeat()))
                .toList();
    }

    /**
     * Retrieves all registry entries whose last heartbeat timestamp occurred before the specified threshold.
     *
     * @param threshold the threshold timestamp for stale session detection
     * @return a list of timed-out session entries
     */
    public List<Entry> entriesOlderThan(Instant threshold) {
        return sessions.entrySet().stream()
                .filter(e -> e.getValue().lastHeartbeat().isBefore(threshold))
                .map(e -> new Entry(e.getKey(), e.getValue().session(), e.getValue().lastHeartbeat()))
                .toList();
    }

    /**
     * Returns the total count of active WebSocket sessions stored in the registry.
     *
     * @return the number of active sessions
     */
    public int size() {
        return sessions.size();
    }

    /**
     * Immutable representation of a session registry entry used for iteration and heartbeat evaluation.
     *
     * @param username      the username associated with the session
     * @param session       the active WebSocket session
     * @param lastHeartbeat the timestamp of the last received ping frame
     */
    public record Entry(String username, WebSocketSession session, Instant lastHeartbeat) {
    }

    private record SessionEntry(WebSocketSession session, Instant lastHeartbeat) {
    }
}