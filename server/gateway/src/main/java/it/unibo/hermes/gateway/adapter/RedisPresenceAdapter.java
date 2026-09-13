package it.unibo.hermes.gateway.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis adapter managing user presence states and gateway routing references via Redis Hashes.
 */
@Component
public class RedisPresenceAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceAdapter.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final String fieldOnline;
    private final String fieldGatewayId;
    private final long ttlSeconds;

    public RedisPresenceAdapter(
            StringRedisTemplate redis,
            @Value("${hermes.presence.key-prefix:hermes:presence:}") String keyPrefix,
            @Value("${hermes.presence.field-online:online}") String fieldOnline,
            @Value("${hermes.presence.field-gateway-id:gatewayId}") String fieldGatewayId,
            @Value("${hermes.presence.ttl-seconds:300}") long ttlSeconds) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
        this.fieldOnline = fieldOnline;
        this.fieldGatewayId = fieldGatewayId;
        this.ttlSeconds = ttlSeconds;
    }

    /**
     * Sets a user's presence state to ONLINE and stores their hosting gateway ID in a Redis Hash with TTL.
     *
     * @param username  the username of the user
     * @param gatewayId the unique identifier of the gateway instance hosting the active session
     */
    public void setOnline(String username, String gatewayId) {
        String key = presenceKey(username);
        try {
            redis.opsForHash().putAll(key, Map.of(
                    fieldOnline, "true",
                    fieldGatewayId, gatewayId
            ));
            redis.expire(key, ttlSeconds, TimeUnit.SECONDS);
            log.debug("Set presence ONLINE for user '{}' on gateway '{}'", username, gatewayId);
        } catch (Exception e) {
            log.warn("Redis unavailable - could not set ONLINE for '{}': {}", username, e.getMessage());
        }
    }

    /**
     * Deletes the user presence hash upon session disconnection.
     *
     * @param username the username of the user
     */
    public void setOffline(String username) {
        try {
            redis.delete(presenceKey(username));
            log.debug("Set presence OFFLINE for user '{}'", username);
        } catch (Exception e) {
            log.warn("Redis unavailable - could not set OFFLINE for '{}': {}", username, e.getMessage());
        }
    }

    /**
     * Refreshes the TTL expiration window for a user's presence hash upon receiving a heartbeat.
     *
     * @param username the username of the user
     */
    public void refreshTtl(String username) {
        try {
            redis.expire(presenceKey(username), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis unavailable - could not refresh TTL for '{}': {}", username, e.getMessage());
        }
    }

    /**
     * Checks whether an active presence record exists and has online=true.
     *
     * @param username the username to check
     * @return {@code true} if the user is online, {@code false} otherwise
     */
    public boolean isOnline(String username) {
        try {
            Object onlineVal = redis.opsForHash().get(presenceKey(username), fieldOnline);
            return onlineVal != null && Boolean.parseBoolean(onlineVal.toString());
        } catch (Exception e) {
            log.warn("Redis unavailable - defaulting isOnline('{}') to false", username);
            return false;
        }
    }

    /**
     * Retrieves the gateway instance ID hosting the specified user's active session.
     *
     * @param username the target username
     * @return the assigned gateway instance ID, or {@code null} if offline or unreachable
     */
    public String getGatewayId(String username) {
        try {
            Object val = redis.opsForHash().get(presenceKey(username), fieldGatewayId);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.warn("Redis unavailable - could not read gatewayId for '{}': {}", username, e.getMessage());
            return null;
        }
    }

    private String presenceKey(String username) {
        return keyPrefix + username;
    }
}
