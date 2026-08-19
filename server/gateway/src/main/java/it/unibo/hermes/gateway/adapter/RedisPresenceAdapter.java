package it.unibo.hermes.gateway.adapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis adapter managing user presence states, gateway routing references, and sequence counters.
 */
@Component
public class RedisPresenceAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceAdapter.class);

    private static final String PRESENCE_PREFIX = "hermes:presence:";
    private static final String ROUTING_PREFIX = "hermes:routing:";

    private final RedisTemplate<String, String> redis;

    @Value("${hermes.presence.ttl-seconds:300}")
    private long ttlSeconds;

    /**
     * Creates the adapter with the configured {@link RedisTemplate}.
     *
     * @param redis the template used for Redis operations
     */
    public RedisPresenceAdapter(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    /**
     * Sets a user's presence state to online and records their hosting gateway instance ID with a TTL.
     *
     * @param username          the unique username
     * @param gatewayInstanceId the identifier of the gateway node hosting the connection
     */
    public void setOnline(String username, String gatewayInstanceId) {
        try {
            redis.opsForValue().set(presenceKey(username), "ONLINE", ttlSeconds, TimeUnit.SECONDS);
            redis.opsForValue().set(routingKey(username), gatewayInstanceId, ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis unavailable - could not set ONLINE for '{}': {}", username, e.getMessage());
        }
    }

    /**
     * Deletes presence and routing records for a user upon session disconnection.
     *
     * @param username the unique username
     */
    public void setOffline(String username) {
        try {
            redis.delete(presenceKey(username));
            redis.delete(routingKey(username));
        } catch (Exception e) {
            log.warn("Redis unavailable - could not set OFFLINE for '{}': {}", username, e.getMessage());
        }
    }

    /**
     * Refreshes the TTL expiration window for a user's presence and routing keys upon receiving a heartbeat.
     *
     * @param username the unique username
     */
    public void refreshTtl(String username) {
        try {
            redis.expire(presenceKey(username), ttlSeconds, TimeUnit.SECONDS);
            redis.expire(routingKey(username), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis unavailable - could not refresh TTL for '{}': {}", username, e.getMessage());
        }
    }

    /**
     * Checks whether an active presence record exists in Redis for the given user.
     *
     * @param username the unique username
     * @return {@code true} if the user is online, {@code false} if offline or if Redis is unreachable
     */
    public boolean isOnline(String username) {
        try {
            return redis.hasKey(presenceKey(username));
        } catch (Exception e) {
            log.warn("Redis unavailable - defaulting isOnline('{}') to false", username);
            return false;
        }
    }

    /**
     * Retrieves the gateway instance ID currently hosting the user's WebSocket connection.
     *
     * @param username the target username
     * @return the assigned gateway instance ID, or {@code null} if unmapped or if Redis is unreachable
     */
    public String getGatewayInstanceId(String username) {
        try {
            return redis.opsForValue().get(routingKey(username));
        } catch (Exception e) {
            log.warn("Redis unavailable - could not read routing for '{}'", username);
            return null;
        }
    }

    private String presenceKey(String username) {
        return PRESENCE_PREFIX + username;
    }

    private String routingKey(String username) {
        return ROUTING_PREFIX + username;
    }
}