package it.unibo.hermes.gateway.application.port.secondary;

/**
 * Secondary port interface for shared, transient presence information
 * used to route deliveries to the right Gateway.
 */
public interface PresencePort {

    /**
     * Sets the user as online, associated with the given gateway identifier.
     *
     * @param username  the username of the user
     * @param gatewayId the identifier of the gateway the user is connected to
     */
    void setOnline(String username, String gatewayId);

    /**
     * Sets the user as offline, associated with the given gateway identifier.
     *
     * @param username  the username of the user
     * @param gatewayId the identifier of the gateway the user is connected to
     */
    void setOffline(String username, String gatewayId);

    /**
     * Refreshes the TTL of the user's presence information.
     *
     * @param username the username of the user
     */
    void refreshTtl(String username);

    /**
     * Refreshes the TTL of the user's presence information
     * or restores it if it was lost.
     *
     * @param username  the username of the user
     * @param gatewayId the identifier of the gateway the user is connected to
     * @return true if the presence information was refreshed or restored, false otherwise
     */
    boolean refreshOrRestore(String username, String gatewayId);

    /**
     * Checks if the user is currently online.
     *
     * @param username the username of the user
     * @return true if the user is online, false otherwise
     */
    boolean isOnline(String username);

    /**
     * Gets the gateway identifier associated with the given username.
     *
     * @param username the username of the user
     * @return the gateway identifier associated with the user, or null if the user is offline
     */
    String getGatewayId(String username);
}
