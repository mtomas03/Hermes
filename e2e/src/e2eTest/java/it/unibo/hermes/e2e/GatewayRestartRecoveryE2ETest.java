package it.unibo.hermes.e2e;

import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static it.unibo.hermes.e2e.support.TestClient.ids;
import static org.junit.jupiter.api.Assertions.*;

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
        alice.awaitAcknowledged(conversationId, before, TIMEOUT);

        int aliceDisconnections = alice.disconnectionCount();
        int bobDisconnections = bob.disconnectionCount();
        Kubectl.restartAndAwaitRollout("gateway-deployment", E2EConfig.get().namespace(), ROLLOUT_TIMEOUT);

        // isConnected() alone can still be true on a session the Gateway has already closed but the client
        // has not noticed yet: require that each client really lost its session and then got a new one
        Poller.pollUntilTrue(() -> alice.disconnectionCount() > aliceDisconnections && alice.isConnected()
                        && bob.disconnectionCount() > bobDisconnections && bob.isConnected(), RECONNECT_TIMEOUT,
                "both clients to lose their session and reconnect after the Gateway restart");

        String after = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("afterRestart"));
        Poller.pollUntil(() -> bob.findLocalMessage(conversationId, after), TIMEOUT,
                "message " + after + " to be delivered in real time after the Gateway restart");
        alice.awaitAcknowledged(conversationId, after, TIMEOUT);

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

    /**
     * Verifies the resilience of the sender: a message submitted while the Gateway restart has cut the
     * session is not lost. The client keeps it in its outbox (PENDING), retransmits it with the same id
     * once the session is back, and it ends up delivered once, accepted by the server (SENT) and
     * acknowledged by the recipient. Unlike the test above, it deliberately sends inside the blackout.
     */
    @Test
    void messageSentWhileTheGatewayIsRestartingIsRetransmittedAfterReconnection() throws Exception {
        alice = new TestClient(TestIds.testId("gwblackoutA"));
        bob = new TestClient(TestIds.testId("gwblackoutB"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);
        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        assertTrue(alice.isConnected() && bob.isConnected(), "precondition: both clients connected");
        int aliceDisconnections = alice.disconnectionCount();

        CompletableFuture<Void> restart = CompletableFuture.runAsync(() ->
                Kubectl.restartAndAwaitRollout("gateway-deployment", E2EConfig.get().namespace(), ROLLOUT_TIMEOUT));

        // the blackout: wait, with a short poll interval, until alice has really lost her session
        Poller.pollUntil(() -> alice.disconnectionCount() > aliceDisconnections && !alice.isConnected()
                        ? Optional.of(true) : Optional.empty(),
                ROLLOUT_TIMEOUT, Duration.ofMillis(20),
                "alice to lose her session because of the Gateway restart");

        String during = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("duringBlackout"));

        assertEquals(MessageStatus.PENDING, alice.findLocalMessage(conversationId, during).orElseThrow().getStatus(),
                "a message composed without a session must stay in the outbox (PENDING)");

        restart.get(ROLLOUT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        Poller.pollUntilTrue(() -> alice.isConnected() && bob.isConnected(), RECONNECT_TIMEOUT,
                "both clients to reconnect after the Gateway restart");

        // retransmitted with the same id: the server's acceptance ACK takes it out of alice's outbox
        Poller.pollUntil(() -> alice.findLocalMessage(conversationId, during)
                        .filter(m -> m.getStatus() == MessageStatus.SENT),
                RECONNECT_TIMEOUT, "message " + during + " to leave alice's outbox (server acceptance ACK)");
        // bob may have been offline when it was delivered: the sync persists it and sends his ACK
        Poller.pollUntil(() -> bob.syncAndPersist(conversationId, TIMEOUT).stream()
                        .filter(m -> m.messageId().equals(during)).findFirst(),
                TIMEOUT, "message " + during + " to reach " + bob.getUsername() + " after the blackout");
        alice.awaitAcknowledged(conversationId, during, TIMEOUT);

        // idempotent retransmission: one copy per client, one entry on the server, the same id throughout
        for (TestClient client : List.of(alice, bob)) {
            assertEquals(1, client.countLocalCopies(conversationId, during),
                    "no duplicate in the history of " + client.getUsername());
            assertEquals(1, ids(client.syncConversation(conversationId, TIMEOUT).messages()).stream()
                            .filter(during::equals).count(),
                    "the server must hold exactly one copy of " + during);
        }
        assertFalse(alice.localMessages(conversationId).stream()
                        .anyMatch(m -> m.getStatus() == MessageStatus.PENDING),
                "alice's outbox must be empty after the recovery");
    }
}
