package it.unibo.hermes.gateway.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;
    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
        session = mock(WebSocketSession.class);
    }

    @Test
    void shouldFindRegisteredSession() {
        registry.register("alice", session);

        Optional<WebSocketSession> found = registry.sessionOf("alice");
        assertThat(found).contains(session);
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void shouldNotFindUnregisteredSession() {
        assertThat(registry.sessionOf("ghost")).isEmpty();
    }

    @Test
    void shouldRemoveSessionOnUnregister() {
        registry.register("alice", session);

        registry.unregister("alice");

        assertThat(registry.sessionOf("alice")).isEmpty();
        assertThat(registry.size()).isZero();
    }

    @Test
    void shouldNotReportStaleSessionAsOlderThanThresholdRightAfterHeartbeat() {
        registry.register("alice", session);
        registry.recordHeartbeat("alice");

        var stale = registry.entriesOlderThan(Instant.now().minusSeconds(60));

        assertThat(stale).isEmpty();
    }

    @Test
    void shouldReportSessionAsStaleWhenThresholdIsInTheFuture() {
        registry.register("alice", session);

        // Any threshold after "now" makes every entry look older than it
        var stale = registry.entriesOlderThan(Instant.now().plusSeconds(60));

        assertThat(stale).hasSize(1);
        assertThat(stale.getFirst().username()).isEqualTo("alice");
    }

    @Test
    void allEntriesShouldReflectCurrentRegistrations() {
        registry.register("alice", session);
        registry.register("bob", mock(WebSocketSession.class));

        assertThat(registry.allEntries()).hasSize(2);
    }
}
