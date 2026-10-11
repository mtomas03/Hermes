package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-End test suite for backend Worker failure and recovery.
 *
 * <p> Tests system resilience by intentionally forcing a Kubernetes rolling restart of the
 * {@code worker} while a message is in transit. Verifies that the system recovers
 * gracefully: the message must not be lost, Kafka consumer group rebalancing must succeed,
 * idempotent processing must handle any redeliveries and the message must eventually reach
 * the {@code ACKNOWLEDGED} state.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class WorkerRestartRecoveryE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final Duration ROLLOUT_TIMEOUT = Duration.ofSeconds(90);
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
    void preservesAndProcessesMessageAcrossWorkerRestart() {
        alice = new TestClient(TestIds.testId("workerrestartA"));
        bob = new TestClient(TestIds.testId("workerrestartB"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);

        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String body = TestIds.messageBody(TestIds.shortId());
        String messageId = alice.sendMessageTo(bob.getUsername(), body);

        Kubectl.restartAndAwaitRollout("worker-deployment", E2EConfig.get().namespace(), ROLLOUT_TIMEOUT);

        Poller.pollUntil(() -> bob.findLocalMessage(conversationId, messageId), TIMEOUT,
                "message " + messageId + " to still reach " + bob.getUsername() + " after a Worker restart");

        InboundMessageDto finalState = Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT)
                        .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                TIMEOUT,
                "message " + messageId + " to reach " + ACKNOWLEDGED + " server-side after a Worker restart");

        assertEquals(ACKNOWLEDGED, finalState.messageStatus());
        assertEquals(body, finalState.content(), "message content must survive the restart intact");
    }
}
