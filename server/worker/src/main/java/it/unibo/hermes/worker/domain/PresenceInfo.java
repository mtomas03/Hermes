package it.unibo.hermes.worker.domain;

/**
 * Transient presence and routing information for a user,
 * read from the Redis cache.
 *
 * @param username  the user whose reachability this describes
 * @param online    true if the user currently has an active WebSocket session
 * @param gatewayId identifier of the gateway holding the user's session;
 *                  meaningful only when {@code online} is true
 */
public record PresenceInfo(
        String username,
        boolean online,
        String gatewayId) {

    /**
     * Creates a {@link PresenceInfo} instance for an offline user with no active session.
     *
     * @param username the unique name of the user
     * @return a presence record with {@code online = false} and {@code gatewayId = null}
     */
    public static PresenceInfo offline(String username) {
        return new PresenceInfo(username, false, null);
    }

    /**
     * Creates a {@link PresenceInfo} instance for an online user connected to a gateway.
     *
     * @param username the unique name of the user
     * @param gatewayId the identifier of the gateway hosting the active session
     * @return a presence record with {@code online = true} and the assigned gateway ID
     */
    public static PresenceInfo online(String username, String gatewayId) {
        return new PresenceInfo(username, true, gatewayId);
    }
}
