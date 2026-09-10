package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.adapter.RedisPresenceAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceTest {

    @Mock
    private RedisPresenceAdapter presenceAdapter;

    private PresenceService service;

    @BeforeEach
    void setUp() {
        service = new PresenceService(presenceAdapter);
    }

    @Test
    void shouldMarkUserOnlineWithGatewayId() {
        service.setOnline("alice", "gw-1");

        verify(presenceAdapter).setOnline("alice", "gw-1");
    }

    @Test
    void shouldMarkUserOffline() {
        service.setOffline("alice");

        verify(presenceAdapter).setOffline("alice");
    }

    @Test
    void shouldRefreshTtlOnHeartbeat() {
        service.refreshTtl("alice");

        verify(presenceAdapter).refreshTtl("alice");
    }

    @Test
    void shouldReportOnlineStatusFromAdapter() {
        when(presenceAdapter.isOnline("alice")).thenReturn(true);

        assertThat(service.isOnline("alice")).isTrue();
    }

    @Test
    void shouldReportOfflineStatusFromAdapter() {
        when(presenceAdapter.isOnline("bob")).thenReturn(false);

        assertThat(service.isOnline("bob")).isFalse();
    }

    @Test
    void shouldReturnGatewayInstanceIdFromAdapter() {
        when(presenceAdapter.getGatewayInstanceId("alice")).thenReturn("gw-2");

        assertThat(service.getGatewayInstanceId("alice")).isEqualTo("gw-2");
    }

    @Test
    void shouldReturnNullGatewayInstanceIdWhenUserOffline() {
        when(presenceAdapter.getGatewayInstanceId("ghost")).thenReturn(null);

        assertThat(service.getGatewayInstanceId("ghost")).isNull();
    }
}
