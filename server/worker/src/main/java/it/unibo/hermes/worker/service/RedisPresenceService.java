package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.domain.PresenceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service responsible for reading user presence and routing information from Redis.
 *
 * <p>The Gateway component manages the presence lifecycle by writing and expiring entries when user
 * WebSocket sessions start and end. The Delivery Worker maintains read-only access to this cache.
 *
 * <p>If Redis is unreachable, this service catches the exception, logs a warning, and returns
 * {@link Optional#empty()} to ensure the system safely treats the recipient as offline.
 */
@Service
public class RedisPresenceService {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceService.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final String fieldOnline;
    private final String fieldGatewayId;

    /**
     * Creates a new {@code RedisPresenceService}.
     *
     * @param redisTemplate  the Redis Spring template used to perform hash operations
     * @param keyPrefix      the prefix appended to the username to construct the Redis key
     * @param fieldOnline    the Redis hash field name indicating online status
     * @param fieldGatewayId the Redis hash field name holding the gateway ID
     */
    public RedisPresenceService(
            StringRedisTemplate redisTemplate,
            @Value("${hermes.presence.key-prefix:hermes:presence:}") String keyPrefix,
            @Value("${hermes.presence.field-online:online}") String fieldOnline,
            @Value("${hermes.presence.field-gateway-id:gatewayId}") String fieldGatewayId) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.fieldOnline = fieldOnline;
        this.fieldGatewayId = fieldGatewayId;
    }

    /**
     * Retrieves the current presence and routing information for a target user from Redis.
     *
     * <p>Fetches all fields of the user's presence hash. If the record exists, it maps the hash
     * attributes to a {@link PresenceInfo} instance; otherwise, or if Redis is unreachable,
     * it defaults safely to an empty result.
     *
     * @param username the unique name of the user to check
     * @return an {@link Optional} containing {@link PresenceInfo} if presence data exists;
     *         {@link Optional#empty()} if no entry is found or if Redis is unreachable
     */
    public Optional<PresenceInfo> getPresence(String username) {
        String redisKey = keyPrefix + username;
        try {
            HashOperations<String, String, String> ops = redisTemplate.opsForHash();
            Map<String, String> fields = ops.entries(redisKey);

            if (fields.isEmpty()) {
                log.debug("No presence entry found for user {}", username);
                return Optional.empty();
            }

            boolean online = Boolean.parseBoolean(fields.get(fieldOnline));
            String gatewayId = fields.get(fieldGatewayId);

            PresenceInfo presence = online
                    ? PresenceInfo.online(username, gatewayId)
                    : PresenceInfo.offline(username);

            log.debug("Presence for user {}: online={} gatewayId={}", username, online, gatewayId);
            return Optional.of(presence);

        } catch (Exception e) {
            log.warn("Redis unavailable when reading presence for user {} - treating as offline: {}",
                    username, e.getMessage());
            return Optional.empty();
        }
    }
}