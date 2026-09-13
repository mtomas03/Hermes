package it.unibo.hermes.gateway.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisPresenceAdapterTest {

    private static final String KEY_PREFIX = "hermes:presence:";
    private static final String FIELD_ONLINE = "online";
    private static final String FIELD_GATEWAY_ID = "gatewayId";
    private static final long TTL_SECONDS = 300L;

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private RedisPresenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new RedisPresenceAdapter(
                redis,
                KEY_PREFIX,
                FIELD_ONLINE,
                FIELD_GATEWAY_ID,
                TTL_SECONDS
        );
    }

    @Test
    void setOnlineSuccess() {
        when(redis.opsForHash()).thenReturn(hashOperations);

        adapter.setOnline("alice", "gateway-1");

        verify(hashOperations).putAll("hermes:presence:alice", Map.of(
                "online", "true",
                "gatewayId", "gateway-1"
        ));
        verify(redis).expire("hermes:presence:alice", 300L, TimeUnit.SECONDS);
    }

    @Test
    void setOnlineRedisException() {
        when(redis.opsForHash()).thenThrow(new RuntimeException("Redis connection error"));

        assertThatCode(() -> adapter.setOnline("alice", "gateway-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void setOfflineSuccess() {
        adapter.setOffline("alice");

        verify(redis).delete("hermes:presence:alice");
    }

    @Test
    void setOfflineRedisException() {
        when(redis.delete(anyString())).thenThrow(new RuntimeException("Redis connection error"));

        assertThatCode(() -> adapter.setOffline("alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void RefreshTTLSuccess() {
        adapter.refreshTtl("alice");

        verify(redis).expire("hermes:presence:alice", 300L, TimeUnit.SECONDS);
    }

    @Test
    void RefreshTTLRedisException() {
        when(redis.expire(anyString(), anyLong(), any())).thenThrow(new RuntimeException("Redis connection error"));

        assertThatCode(() -> adapter.refreshTtl("alice"))
                .doesNotThrowAnyException();
    }

    @Test
    void isOnlineTrue() {
        when(redis.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("hermes:presence:alice", "online")).thenReturn("true");

        boolean result = adapter.isOnline("alice");

        assertThat(result).isTrue();
    }

    @Test
    void isOnlineFalse() {
        when(redis.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("hermes:presence:alice", "online")).thenReturn(null);

        boolean result = adapter.isOnline("alice");

        assertThat(result).isFalse();
    }

    @Test
    void isOnlineException() {
        when(redis.opsForHash()).thenThrow(new RuntimeException("Redis failure"));

        boolean result = adapter.isOnline("alice");

        assertThat(result).isFalse();
    }

    @Test
    void getGatewayIdFound() {
        when(redis.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("hermes:presence:alice", "gatewayId")).thenReturn("gateway-1");

        String gatewayId = adapter.getGatewayId("alice");

        assertThat(gatewayId).isEqualTo("gateway-1");
    }

    @Test
    void getGatewayIdNotFound() {
        when(redis.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("hermes:presence:alice", "gatewayId")).thenReturn(null);

        String gatewayId = adapter.getGatewayId("alice");

        assertThat(gatewayId).isNull();
    }

    @Test
    void getGatewayIdException() {
        when(redis.opsForHash()).thenThrow(new RuntimeException("Redis failure"));

        String gatewayId = adapter.getGatewayId("alice");

        assertThat(gatewayId).isNull();
    }
}
