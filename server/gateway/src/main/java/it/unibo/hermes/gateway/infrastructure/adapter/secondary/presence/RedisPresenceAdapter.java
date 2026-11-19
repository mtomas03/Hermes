package it.unibo.hermes.gateway.infrastructure.adapter.secondary.presence;

import it.unibo.hermes.gateway.application.port.secondary.PresencePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Secondary adapter implementing {@link PresencePort} on top of Redis
 * by storing and managing user presence information in a Redis hash with a TTL.
 */
@Component
public class RedisPresenceAdapter implements PresencePort {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceAdapter.class);

    /**
     * Deletes the user presence hash only if the gatewayId field matches the provided value.
     * KEYS[1] = key; ARGV = fieldGatewayId, gatewayId. Returns 1 if deleted, else 0.
     */
    private static final RedisScript<Long> DELETE_IF_GATEWAY_MATCH_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2] then " +
                    "return redis.call('DEL', KEYS[1]) " +
                    "else " +
                    "return 0 " +
                    "end",
            Long.class
    );

    /**
     * Refreshes the TTL of an existing presence entry or, if the entry is missing, recreates it.
     * KEYS[1] = key; ARGV = fieldOnline, fieldGatewayId, gatewayId, ttlSeconds. Returns 1 if recreated, else 0.
     */
    private static final RedisScript<Long> REFRESH_OR_RESTORE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('EXISTS', KEYS[1]) == 1 then " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
                    "return 0 " +
                    "else " +
                    "redis.call('HSET', KEYS[1], ARGV[1], 'true', ARGV[2], ARGV[3]) " +
                    "redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
                    "return 1 " +
                    "end",
            Long.class
    );

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

    @Override
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

    @Override
    public void setOffline(String username, String gatewayId) {
        try {
            String key = presenceKey(username);
            Long result = redis.execute(
                    DELETE_IF_GATEWAY_MATCH_SCRIPT,
                    Collections.singletonList(key),
                    fieldGatewayId,
                    gatewayId
            );
            if (Long.valueOf(1L).equals(result)) {
                log.debug("Set presence OFFLINE for user '{}' on gateway '{}'", username, gatewayId);
            } else {
                log.debug("Skipped setting OFFLINE for user '{}' on gateway '{}' (session active on another gateway)", username, gatewayId);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable - could not set OFFLINE for '{}': {}", username, e.getMessage());
        }
    }

    @Override
    public void refreshTtl(String username) {
        try {
            redis.expire(presenceKey(username), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis unavailable - could not refresh TTL for '{}': {}", username, e.getMessage());
        }
    }

    @Override
    public boolean refreshOrRestore(String username, String gatewayId) {
        try {
            Long restored = redis.execute(
                    REFRESH_OR_RESTORE_SCRIPT,
                    Collections.singletonList(presenceKey(username)),
                    fieldOnline, fieldGatewayId, gatewayId, String.valueOf(ttlSeconds));
            boolean recreated = Long.valueOf(1L).equals(restored);
            if (recreated) {
                log.info("Presence of '{}' was missing - restored ONLINE on gateway '{}'", username, gatewayId);
            }
            return recreated;
        } catch (Exception e) {
            log.warn("Redis unavailable - could not refresh/restore presence for '{}': {}", username, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isOnline(String username) {
        try {
            Object onlineVal = redis.opsForHash().get(presenceKey(username), fieldOnline);
            return onlineVal != null && Boolean.parseBoolean(onlineVal.toString());
        } catch (Exception e) {
            log.warn("Redis unavailable - defaulting isOnline('{}') to false", username);
            return false;
        }
    }

    @Override
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
