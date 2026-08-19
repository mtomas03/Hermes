package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.RedisPresenceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service managing user online presence states and gateway routing information in Redis.
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final RedisPresenceAdapter presenceAdapter;

    /**
     * Creates the presence service.
     *
     * @param presenceAdapter the adapter executing presence operations in Redis
     */
    public PresenceService(RedisPresenceAdapter presenceAdapter) {
        this.presenceAdapter = presenceAdapter;
    }

    /**
     * Marks a user as online and records the hosting gateway instance identifier in Redis.
     *
     * @param username          the username of the connecting client
     * @param gatewayInstanceId the unique identifier of the gateway instance hosting the active session
     */
    public void setOnline(String username, String gatewayInstanceId) {
        presenceAdapter.setOnline(username, gatewayInstanceId);
        log.debug("User '{}' marked ONLINE on gateway '{}'", username, gatewayInstanceId);
    }

    /**
     * Marks a user as offline in Redis and removes associated gateway routing information.
     *
     * @param username the username of the disconnecting client
     */
    public void setOffline(String username) {
        presenceAdapter.setOffline(username);
        log.debug("User '{}' marked OFFLINE", username);
    }

    /**
     * Refreshes the TTL expiration window of a user's presence record upon receiving a heartbeat.
     *
     * @param username the username of the active client
     */
    public void refreshTtl(String username) {
        presenceAdapter.refreshTtl(username);
    }

    /**
     * Checks whether a user currently has an active online presence record in Redis.
     *
     * @param username the username to check
     * @return {@code true} if the user has an active session, {@code false} otherwise
     */
    public boolean isOnline(String username) {
        return presenceAdapter.isOnline(username);
    }

    /**
     * Retrieves the identifier of the gateway instance currently hosting the user's active session.
     *
     * @param username the username of the target user
     * @return the hosting gateway instance identifier, or {@code null} if the user is offline
     */
    public String getGatewayInstanceId(String username) {
        return presenceAdapter.getGatewayInstanceId(username);
    }
}