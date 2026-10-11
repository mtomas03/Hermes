package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End test suite for Gateway restart and recovery.
 *
 * <p> The Gateway terminates every client WebSocket session, so a rolling restart drops all sessions
 * while Kafka, the Worker and the databases keep running. The system must recover by itself:
 * the Clients reconnect (exponential back-off in the Client), real-time messaging resumes in both directions,
 * delivery is acknowledged again and the whole conversation, including what was exchanged before the restart,
 * is still consistent through full synchronisation.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class GatewayRestartRecoveryE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final Duration ROLLOUT_TIMEOUT = Duration.ofSeconds(240);
    private static final Duration RECONNECT_TIMEOUT = Duration.ofSeconds(120);
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
    void clientsReconnectAndResumeMessagingAfterGatewayRestart() {
        alice = new TestClient(TestIds.testId("gwrestartA"));
        bob = new TestClient(TestIds.testId("gwrestartB"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);
        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());

        String before = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("beforeRestart"));
        Poller.pollUntil(() -> bob.findLocalMessage(conversationId, before), TIMEOUT,
                "message " + before + " to reach " + bob.getUsername() + " before the Gateway restart");
        awaitAcknowledged(conversationId, before);

        Kubectl.restartAndAwaitRollout("gateway-deployment", E2EConfig.get().namespace(), ROLLOUT_TIMEOUT);

        Poller.pollUntilTrue(() -> alice.isConnected() && bob.isConnected(), RECONNECT_TIMEOUT,
                "both clients to reconnect after the Gateway restart");

        String after = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("afterRestart"));
        Poller.pollUntil(() -> bob.findLocalMessage(conversationId, after), TIMEOUT,
                "message " + after + " to be delivered in real time after the Gateway restart");
        awaitAcknowledged(conversationId, after);

        String reply = bob.sendMessageTo(alice.getUsername(), TestIds.messageBody("replyAfterRestart"));
        Poller.pollUntil(() -> alice.findLocalMessage(conversationId, reply), TIMEOUT,
                "reply " + reply + " to be delivered in real time after the Gateway restart");

        List<String> expected = List.of(before, after, reply);
        for (TestClient client : List.of(alice, bob)) {
            Poller.pollUntilTrue(
                    () -> ids(client.syncAndPersist(conversationId, TIMEOUT)).containsAll(expected),
                    TIMEOUT, "full sync of " + client.getUsername() + " to contain all three messages");
            assertEquals(expected, ids(client.syncAndPersist(conversationId, TIMEOUT)),
                    "full sync must return exactly the exchanged messages in causal order");
            assertEquals(expected.size(), client.localMessages(conversationId).size(),
                    "no duplicate or missing messages in the local state of " + client.getUsername());
        }
        assertTrue(alice.isConnected() && bob.isConnected(), "sessions stay up after the recovery");
    }

    private void awaitAcknowledged(String conversationId, String messageId) {
        Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT)
                        .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                TIMEOUT, "server-side state of message " + messageId + " to become ACKNOWLEDGED");
    }

    private static List<String> ids(List<InboundMessageDto> messages) {
        return messages.stream().map(InboundMessageDto::messageId).toList();
    }
}
