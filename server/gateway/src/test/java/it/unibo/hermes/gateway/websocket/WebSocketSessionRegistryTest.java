package it.unibo.hermes.gateway.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebSocketSessionRegistryTest {

    private WebSocketSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketSessionRegistry();
    }

    @Test
    void shouldReportRegisteredUserAsConnected() {
        registry.register("alice");

        assertThat(registry.isConnected("alice")).isTrue();
        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void shouldNotReportUnregisteredUserAsConnected() {
        assertThat(registry.isConnected("ghost")).isFalse();
    }

    @Test
    void shouldRemoveUserOnUnregister() {
        registry.register("alice");

        registry.unregister("alice");

        assertThat(registry.isConnected("alice")).isFalse();
        assertThat(registry.size()).isZero();
    }

    @Test
    void registeringTheSameUserTwiceShouldNotDuplicateEntries() {
        registry.register("alice");
        registry.register("alice");

        assertThat(registry.size()).isEqualTo(1);
    }

    @Test
    void allUsernamesShouldReflectCurrentRegistrations() {
        registry.register("alice");
        registry.register("bob");

        assertThat(registry.allUsernames()).containsExactlyInAnyOrder("alice", "bob");
    }

    @Test
    void unregisteringAnUnknownUserShouldBeANoOp() {
        registry.unregister("ghost");

        assertThat(registry.size()).isZero();
    }
}
