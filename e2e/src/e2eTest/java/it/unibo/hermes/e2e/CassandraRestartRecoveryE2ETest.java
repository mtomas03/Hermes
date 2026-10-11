package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-End test suite for Cassandra restart and recovery.
 *
 * <p> Cassandra runs as a single node on a PersistentVolumeClaim, so a restart
 * makes message storage unavailable for a while, but must not lose
 * persisted messages, and the Gateway and the Worker must resume using the database by themselves.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class CassandraRestartRecoveryE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final Duration ROLLOUT_TIMEOUT = Duration.ofSeconds(300);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(240);
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
    void persistedHistorySurvivesCassandraRestartAndNewMessagesFlowAgain() {
        alice = new TestClient(TestIds.testId("cassA"));
        bob = new TestClient(TestIds.testId("cassB"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);
        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());

        String body = TestIds.messageBody("beforeCassandraRestart");
        String before = alice.sendMessageTo(bob.getUsername(), body);
        Poller.pollUntil(() -> bob.findLocalMessage(conversationId, before), TIMEOUT,
                "message " + before + " to reach " + bob.getUsername());
        InboundMessageDto baseline = Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, before, TIMEOUT)
                        .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                TIMEOUT, "message " + before + " to be persisted and ACKNOWLEDGED before the restart");
        assertEquals(body, baseline.content());

        Kubectl.restartStatefulSetAndAwait("cassandra-statefulset", E2EConfig.get().namespace(), ROLLOUT_TIMEOUT);

        // recovery 1: the Gateway reads the history again, with the same identity and content, once
        InboundMessageDto recovered = Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, before, TIMEOUT), RECOVERY_TIMEOUT,
                "message " + before + " to be readable again through full sync after the Cassandra restart");
        assertEquals(before, recovered.messageId());
        assertEquals(body, recovered.content());
        assertEquals(List.of(before), ids(bob.syncAndPersist(conversationId, TIMEOUT)),
                "the recipient must see the pre-restart history exactly once");

        // recovery 2: the Worker persists and delivers again and ACKs work again
        String after = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("afterCassandraRestart"));
        Poller.pollUntil(() -> bob.findLocalMessage(conversationId, after), RECOVERY_TIMEOUT,
                "message " + after + " to be delivered in real time after the Cassandra restart");
        Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, after, TIMEOUT)
                        .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                RECOVERY_TIMEOUT, "message " + after + " to be persisted and ACKNOWLEDGED after the restart");

        // convergence: both participants see the same history, in order, without duplicates
        List<String> expected = List.of(before, after);
        assertEquals(expected, ids(alice.syncConversation(conversationId, TIMEOUT).messages()));
        assertEquals(expected, ids(bob.syncAndPersist(conversationId, TIMEOUT)));
        assertEquals(expected.size(), bob.localMessages(conversationId).size(), "no local duplicates");
    }

    private static List<String> ids(List<InboundMessageDto> messages) {
        return messages.stream().map(InboundMessageDto::messageId).toList();
    }
}
