package it.unibo.hermes.gateway.infrastructure.adapter.secondary.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    @Test
    void shouldReportRegisteredUserAsConnected() {
        registry.register("alice", "s1");

        assertThat(registry.isConnected("alice")).isTrue();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void shouldNotReportUnregisteredUserAsConnected() {
        assertThat(registry.isConnected("ghost")).isFalse();
    }

    @Test
    void shouldRemoveUserWhenTheOnlySessionIsUnregistered() {
        registry.register("alice", "s1");

        boolean last = registry.unregister("alice", "s1");

        assertThat(last).isTrue();
        assertThat(registry.isConnected("alice")).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void registeringTheSameSessionTwiceShouldNotDuplicateEntries() {
        registry.register("alice", "s1");
        registry.register("alice", "s1");

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.unregister("alice", "s1")).isTrue();
    }

    @Test
    void closingOneOfTwoOverlappingSessionsShouldKeepTheUserConnected() {
        registry.register("alice", "old-session");
        registry.register("alice", "new-session");

        boolean last = registry.unregister("alice", "old-session");

        assertThat(last).isFalse();
        assertThat(registry.isConnected("alice")).isTrue();
        assertThat(registry.allUsernames()).containsExactly("alice");
    }

    @Test
    void userShouldDisconnectOnlyWhenTheLastOverlappingSessionCloses() {
        registry.register("alice", "s1");
        registry.register("alice", "s2");

        assertThat(registry.unregister("alice", "s1")).isFalse();
        assertThat(registry.unregister("alice", "s2")).isTrue();
        assertThat(registry.isConnected("alice")).isFalse();
    }

    @Test
    void duplicateDisconnectOfTheSameSessionShouldNotReportALastSessionTwice() {
        registry.register("alice", "s1");

        assertThat(registry.unregister("alice", "s1")).isTrue();
        assertThat(registry.unregister("alice", "s1")).isFalse();
    }

    @Test
    void unregisteringAnUnknownSessionShouldNotAffectOtherSessions() {
        registry.register("alice", "s1");

        assertThat(registry.unregister("alice", "other")).isFalse();
        assertThat(registry.isConnected("alice")).isTrue();
    }

    @Test
    void allUsernamesShouldReflectCurrentRegistrations() {
        registry.register("alice", "s1");
        registry.register("bob", "s2");

        assertThat(registry.allUsernames()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void unregisteringAnUnknownUserShouldBeANoOp() {
        assertThat(registry.unregister("ghost", "s1")).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void concurrentConnectAndDisconnectNeverLoseALiveSession() throws Exception {
        registry.register("alice", "keeper");
        try (ExecutorService pool = Executors.newFixedThreadPool(8);){
            for (int i = 0; i < 100; i++) {
                String id = "s" + i;
                pool.submit(() -> {
                    registry.register("alice", id);
                    registry.unregister("alice", id);
                });
            }
            pool.shutdown();
            assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(registry.isConnected("alice")).isTrue();
        assertThat(registry.unregister("alice", "keeper")).isTrue();
    }
}
