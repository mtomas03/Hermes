package it.unibo.hermes.worker.adapter;

import it.unibo.hermes.worker.domain.PresenceInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Redis adapter providing read-only presence lookups for Worker routing decisions.
 */
@Component
public class RedisPresenceAdapter {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceAdapter.class);

    private final StringRedisTemplate redisTemplate;
    private final String keyPrefix;
    private final String fieldOnline;
    private final String fieldGatewayId;

    public RedisPresenceAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${hermes.presence.key-prefix:hermes:presence:}") String keyPrefix,
            @Value("${hermes.presence.field-online:online}") String fieldOnline,
            @Value("${hermes.presence.field-gateway-id:gatewayId}") String fieldGatewayId) {
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.fieldOnline = fieldOnline;
        this.fieldGatewayId = fieldGatewayId;
    }

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

            return Optional.of(presence);

        } catch (Exception e) {
            log.warn("Redis unavailable when reading presence for user {} - treating as offline: {}",
                    username, e.getMessage());
            return Optional.empty();
        }
    }
}
