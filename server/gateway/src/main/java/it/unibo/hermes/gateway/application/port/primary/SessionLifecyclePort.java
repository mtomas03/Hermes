package it.unibo.hermes.gateway.application.port.primary;

/**
 * Primary port interface for managing the lifecycle of client sessions.
 */
public interface SessionLifecyclePort {

    /**
     * Records that a client session has been established.
     *
     * @param username the authenticated username
     * @param sessionId the STOMP session identifier
     */
    void clientConnected(String username, String sessionId);

    /**
     * Records that a client session has ended.
     *
     * <p> The user is reported offline only when no other session
     * of the same user is still active.
     *
     * @param username the authenticated username
     * @param sessionId the STOMP session identifier
     */
    void clientDisconnected(String username, String sessionId);
}
