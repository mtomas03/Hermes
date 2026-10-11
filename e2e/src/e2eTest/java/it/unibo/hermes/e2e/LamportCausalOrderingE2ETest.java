package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End test suite for logical ordering guarantees (Lamport).
 *
 * <p> Verifies how a client orders the data it already possesses:
 * causality is reflected in the timestamps assigned by the clients and the
 * order returned by the server upon full synchronisation corresponds to the logical order.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class LamportCausalOrderingE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();

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
    void causallyRelatedMessagesGetIncreasingTimestampsAndAreSyncedInCausalOrder() {
        alice = new TestClient(TestIds.testId("lampA"));
        bob = new TestClient(TestIds.testId("lampB"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);
        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());

        String m1 = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("m1"));
        Message m1AtBob = Poller.pollUntil(() -> bob.findLocalMessage(conversationId, m1), TIMEOUT,
                "m1 to reach " + bob.getUsername());

        String r1 = bob.sendMessageTo(alice.getUsername(), TestIds.messageBody("r1"));
        Message r1AtAlice = Poller.pollUntil(() -> alice.findLocalMessage(conversationId, r1), TIMEOUT,
                "r1 to reach " + alice.getUsername());

        String m2 = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("m2"));
        Message m2AtBob = Poller.pollUntil(() -> bob.findLocalMessage(conversationId, m2), TIMEOUT,
                "m2 to reach " + bob.getUsername());

        assertTrue(m1AtBob.getLogicalTimestamp() < r1AtAlice.getLogicalTimestamp(),
                "a reply must carry a greater timestamp than the message it answers");
        assertTrue(r1AtAlice.getLogicalTimestamp() < m2AtBob.getLogicalTimestamp(),
                "a message sent after receiving r1 must carry a greater timestamp than r1");

        List<String> syncedOrder = alice.syncConversation(conversationId, TIMEOUT).messages().stream()
                .map(InboundMessageDto::messageId).toList();
        assertEquals(List.of(m1, r1, m2), syncedOrder,
                "full synchronization must return the conversation in causal (logical timestamp) order");
    }

    @Test
    void offlineBurstIsSyncedInSendOrderWithStrictlyIncreasingTimestamps() {
        alice = new TestClient(TestIds.testId("burstA"));
        bob = new TestClient(TestIds.testId("burstB"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());

        List<String> sentIds = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sentIds.add(alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("burst" + i)));
        }

        Poller.pollUntilTrue(
                () -> alice.syncConversation(conversationId, TIMEOUT).messages().stream()
                        .map(InboundMessageDto::messageId).toList().containsAll(sentIds),
                TIMEOUT, "all burst messages to be persisted server-side");

        List<InboundMessageDto> bobView = bob.syncConversation(conversationId, TIMEOUT).messages();
        assertEquals(sentIds, bobView.stream().map(InboundMessageDto::messageId).toList(),
                "the recipient must get the burst in send order");
        for (int i = 1; i < bobView.size(); i++) {
            assertTrue(bobView.get(i - 1).logicalTimestamp() < bobView.get(i).logicalTimestamp(),
                    "timestamps of one sender must be strictly increasing");
        }
    }
}
