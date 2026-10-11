package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End test suite for Redis restart and recovery.
 *
 * <p> Redis maintains only a transient presence/routing state and runs without persistence, so a restart clears it
 * while WebSocket sessions stay open. Until the Gateway re-asserts the presence of its connected users
 * (heartbeat), the Worker sees the recipient as offline: messages are persisted but not pushed.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class RedisRestartRecoveryE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final Duration ROLLOUT_TIMEOUT = Duration.ofSeconds(180);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(150);
    private static final Duration PROBE_INTERVAL = Duration.ofSeconds(5);
    private static final String ACKNOWLEDGED = "ACKNOWLEDGED";

    private TestClient alice;
    private TestClient bob;

    @AfterEach
    void tearDown() {
        if (alice != null) {
            alice.close();
        }
        if (bob != null) {
            bob.close();
        }
    }

    @Test
    void realTimeDeliveryResumesAfterRedisRestartWithoutReconnectAndWithoutLosingMessages() {
        alice = new TestClient(TestIds.testId("redisA"));
        bob = new TestClient(TestIds.testId("redisB"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);
        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());

        String before = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("beforeRedisRestart"));
        Poller.pollUntil(() -> bob.findLocalMessage(conversationId, before), TIMEOUT,
                "message " + before + " to reach " + bob.getUsername() + " before the Redis restart");
        awaitAcknowledged(conversationId, before);

        Kubectl.restartAndAwaitRollout("redis-deployment", E2EConfig.get().namespace(), ROLLOUT_TIMEOUT);

        // Presence recovery is only observable through delivery.
        // Probe with a new message per attempt: the first
        // probe that reaches Bob in real time proves recovery.
        List<String> probes = new ArrayList<>();
        String delivered = Poller.pollUntil(() -> {
            Optional<String> pushed = probes.stream()
                    .filter(id -> bob.findLocalMessage(conversationId, id).isPresent())
                    .findFirst();
            if (pushed.isEmpty()) {
                probes.add(alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("probe" + probes.size())));
            }
            return pushed;
        }, RECOVERY_TIMEOUT, PROBE_INTERVAL,
                "real-time delivery to " + bob.getUsername() + " to resume after the Redis restart (presence restored)");
        assertTrue(bob.isConnected() && alice.isConnected(), "recovery must not have needed a disconnection");
        awaitAcknowledged(conversationId, delivered);

        List<String> expected = new ArrayList<>();
        expected.add(before);
        expected.addAll(probes);
        Poller.pollUntilTrue(
                () -> ids(alice.syncConversation(conversationId, TIMEOUT).messages()).containsAll(expected),
                RECOVERY_TIMEOUT, "all messages sent around the Redis restart to be persisted");

        assertEquals(expected, ids(alice.syncConversation(conversationId, TIMEOUT).messages()),
                "the persisted history must be intact, in send order, without duplicates");
        assertEquals(expected, ids(bob.syncAndPersist(conversationId, TIMEOUT)),
                "the recipient must recover everything that was not pushed");
        assertEquals(expected.size(), bob.localMessages(conversationId).size(), "no local duplicates");
    }

    private void awaitAcknowledged(String conversationId, String messageId) {
        Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT)
                        .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                RECOVERY_TIMEOUT, "server-side state of message " + messageId + " to become ACKNOWLEDGED");
    }

    private static List<String> ids(List<InboundMessageDto> messages) {
        return messages.stream().map(InboundMessageDto::messageId).toList();
    }
}
