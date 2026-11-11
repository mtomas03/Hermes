package it.unibo.hermes.gateway.application.port.primary;

/**
 * Primary port interface for managing the lifecycle of client sessions.
 */
public interface SessionLifecyclePort {

    /**
     * Records that a client session has been established.
     *
     * @param username the authenticated username
     */
    void clientConnected(String username);

    /**
     * Records that a client session has ended.
     *
     * @param username the authenticated username
     */
    void clientDisconnected(String username);
}
