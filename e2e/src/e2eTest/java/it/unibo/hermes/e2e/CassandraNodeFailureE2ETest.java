package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-End test suite for Cassandra node failure and recovery.
 *
 * <p> One of the three Cassandra nodes goes down. With RF=3 and LOCAL_QUORUM (2 of 3),
 * reads and writes must keep working; when the node comes back,
 * the cluster converges to three nodes and no message is lost or duplicated.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class CassandraNodeFailureE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final Duration SCALE_TIMEOUT = Duration.ofSeconds(600);
    private static final String NAMESPACE = E2EConfig.get().namespace();
    private static final String ACKNOWLEDGED = "ACKNOWLEDGED";

    private TestClient alice;
    private TestClient bob;

    @AfterEach
    void tearDown() {
        if (alice != null) alice.close();
        if (bob != null) bob.close();
    }

    @Test
    void messagingSurvivesTheLossOfOneCassandraNodeAndClusterReconverges() {
        assertEquals(3, Kubectl.readyReplicas("statefulset", "cassandra-statefulset", NAMESPACE),
                "precondition: a healthy 3-node Cassandra cluster");
        assertEquals(3, upNormalNodes(), "precondition: all three nodes are up");

        alice = new TestClient(TestIds.testId("cnfA"));
        bob = new TestClient(TestIds.testId("cnfB"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);
        String conv = TestIds.conversationId(alice.getUsername(), bob.getUsername());

        String before = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("beforeNodeLoss"));
        awaitDeliveredAndAcknowledged(conv, before);

        boolean scaledDown = false;
        String during;
        try {
            Kubectl.scaleStatefulSet("cassandra-statefulset", 2, NAMESPACE, Duration.ofSeconds(30));
            scaledDown = true;
            Poller.pollUntilTrue(() -> Kubectl.readyReplicas("statefulset", "cassandra-statefulset", NAMESPACE) == 2,
                    SCALE_TIMEOUT, "the third Cassandra node to be stopped");

            // reads and writes must keep working on quorum 2/3
            assertEquals(before, alice.findRemoteMessage(conv, before, TIMEOUT).orElseThrow().messageId());
            during = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("duringNodeLoss"));
            awaitDeliveredAndAcknowledged(conv, during);
        } finally {
            if (scaledDown) {
                Kubectl.scaleStatefulSet("cassandra-statefulset", 3, NAMESPACE, Duration.ofSeconds(30));
            }
        }

        Poller.pollUntilTrue(() -> Kubectl.readyReplicas("statefulset", "cassandra-statefulset", NAMESPACE) == 3
                        && upNormalNodes() == 3,
                SCALE_TIMEOUT, "the Cassandra cluster to be back to three up nodes");

        String after = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("afterNodeRecovery"));
        awaitDeliveredAndAcknowledged(conv, after);

        List<String> expected = List.of(before, during, after);
        assertEquals(expected, ids(alice.syncConversation(conv, TIMEOUT).messages()),
                "history must be complete, ordered and without duplicates");
        assertEquals(expected, ids(bob.syncAndPersist(conv, TIMEOUT)));
        assertEquals(expected.size(), bob.localMessages(conv).size(), "no local duplicates");
    }

    private void awaitDeliveredAndAcknowledged(String conv, String messageId) {
        Poller.pollUntil(() -> bob.findLocalMessage(conv, messageId), TIMEOUT,
                "message " + messageId + " to reach the recipient");
        AtomicReference<String> lastSeen =
                new AtomicReference<>("no sync response yet");
        try {
            Poller.pollUntil(() -> {
                var remote = alice.findRemoteMessage(conv, messageId, TIMEOUT);
                lastSeen.set(remote.map(InboundMessageDto::messageStatus).orElse("message absent from sync"));
                return remote.filter(m -> ACKNOWLEDGED.equals(m.messageStatus()));
            }, TIMEOUT, "message " + messageId + " to be persisted and ACKNOWLEDGED");
        } catch (AssertionError e) {
            throw new AssertionError(e.getMessage() + " | last status seen in sync: " + lastSeen.get(), e);
        }
    }

    private static int upNormalNodes() {
        try {
            String out = Kubectl.customKubectlCommand(
                    Duration.ofSeconds(60), "-n", NAMESPACE, "exec", "cassandra-statefulset-0",
                    "--", "nodetool", "status");
            return (int) out.lines().filter(l -> l.startsWith("UN")).count();
        } catch (AssertionError e) {
            return -1; // node 0 not reachable yet: keep polling
        }
    }

    private static List<String> ids(List<InboundMessageDto> messages) {
        return messages.stream().map(InboundMessageDto::messageId).toList();
    }
}
