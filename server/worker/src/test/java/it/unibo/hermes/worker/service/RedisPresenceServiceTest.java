package it.unibo.hermes.worker.service;

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
class RedisPresenceServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private HashOperations<String, String, String> hashOperations;

    private RedisPresenceService service;

    @BeforeEach
    void setUp() {
        service = new RedisPresenceService(
                redisTemplate, "hermes:presence:",
                "online", "gatewayId");
    }

    @Test
    void shouldReturnEmptyWhenNoPresenceEntryExists() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("hermes:presence:alice")).thenReturn(Map.of());

        Optional<PresenceInfo> result = service.getPresence("alice");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldReturnOnlinePresenceWhenFlagIsTrue() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("hermes:presence:alice"))
                .thenReturn(Map.of("online", "true", "gatewayId", "gw-1"));

        Optional<PresenceInfo> result = service.getPresence("alice");

        assertThat(result).isPresent();
        assertThat(result.get().online()).isTrue();
        assertThat(result.get().gatewayId()).isEqualTo("gw-1");
    }

    @Test
    void shouldReturnOfflinePresenceWhenFlagIsFalse() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("hermes:presence:bob"))
                .thenReturn(Map.of("online", "false"));

        Optional<PresenceInfo> result = service.getPresence("bob");

        assertThat(result).isPresent();
        assertThat(result.get().online()).isFalse();
    }

    @Test
    void shouldTreatMissingOnlineFieldAsOffline() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("hermes:presence:carol"))
                .thenReturn(Map.of("gatewayId", "gw-2"));

        Optional<PresenceInfo> result = service.getPresence("carol");

        assertThat(result).isPresent();
        assertThat(result.get().online()).isFalse();
    }

    @Test
    void shouldReturnEmptyWhenRedisThrows() {
        when(redisTemplate.opsForHash()).thenThrow(new RuntimeException("connection refused"));

        Optional<PresenceInfo> result = service.getPresence("alice");

        // Redis unavailability degrades safely to "treat as offline"
        assertThat(result).isEmpty();
    }
}
