package it.unibo.hermes.worker.adapter;

import it.unibo.hermes.worker.domain.PresenceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisPresenceAdapterTest {

    private static final String KEY_PREFIX = "hermes:presence:";
    private static final String FIELD_ONLINE = "online";
    private static final String FIELD_GATEWAY_ID = "gatewayId";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    private RedisPresenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisPresenceAdapter(
                redisTemplate,
                KEY_PREFIX,
                FIELD_ONLINE,
                FIELD_GATEWAY_ID);
    }

    @Test
    void shouldReturnEmptyWhenNoPresenceEntryExists() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("hermes:presence:alice")).thenReturn(Map.of());

        Optional<PresenceInfo> result = adapter.getPresence("alice");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnOnlinePresenceWhenFlagIsTrue() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("hermes:presence:alice"))
                .thenReturn(Map.of("online", "true", "gatewayId", "gateway-1"));

        Optional<PresenceInfo> result = adapter.getPresence("alice");

        assertThat(result).isPresent();
        assertThat(result.get().online()).isTrue();
        assertThat(result.get().gatewayId()).isEqualTo("gateway-1");
    }

    @Test
    void shouldReturnOfflinePresenceWhenFlagIsFalse() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("hermes:presence:bob"))
                .thenReturn(Map.of("online", "false"));

        Optional<PresenceInfo> result = adapter.getPresence("bob");

        assertThat(result).isPresent();
        assertThat(result.get().online()).isFalse();
    }

    @Test
    void shouldTreatMissingOnlineFieldAsOffline() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("hermes:presence:carol"))
                .thenReturn(Map.of("gatewayId", "gateway-2"));

        Optional<PresenceInfo> result = adapter.getPresence("carol");

        assertThat(result).isPresent();
        assertThat(result.get().online()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenRedisThrows() {
        when(redisTemplate.opsForHash()).thenThrow(new RuntimeException("Redis connection refused"));

        Optional<PresenceInfo> result = adapter.getPresence("alice");

        assertThat(result).isEmpty();
    }
}
