package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Worker failure and recovery test.
 *
 * <p> Sends a message, then forces a restart of the Worker while the message is mid-processing
 * and verifies the system recovers: the message is not lost
 * and eventually reaches {@code ACKNOWLEDGED} once the Worker is back.
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
