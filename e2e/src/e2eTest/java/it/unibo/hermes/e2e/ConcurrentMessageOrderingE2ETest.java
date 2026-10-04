package it.unibo.hermes.e2e;

import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrent message ordering test.
 *
 * <p> Sends several messages from A to B in rapid succession and verifies all of them eventually
 * arrive with distinct ids, no duplicates and in a deterministic order:
 * {@code logical_timestamp} then {@code message_id} as a tie-breaker.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class ConcurrentMessageOrderingE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final int MESSAGE_COUNT = 5;

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
    void deliversAllMessagesFromARapidBurstExactlyOnceAndInDeterministicOrder() {
        alice = new TestClient(TestIds.testId("burstsender"));
        bob = new TestClient(TestIds.testId("burstrecipient"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);

        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String runId = TestIds.shortId();

        List<String> sentIds = new ArrayList<>();
        for (int i = 0; i < MESSAGE_COUNT; i++) {
            sentIds.add(alice.sendMessageTo(bob.getUsername(), "E2E_TEST_" + runId + "_msg" + i));
        }

        assertEquals(MESSAGE_COUNT, Set.copyOf(sentIds).size(), "generated message ids must be distinct");

        Poller.pollUntilTrue(
                () -> {
                    List<Message> local = bob.localMessages(conversationId);
                    Set<String> receivedIds = local.stream().map(Message::getMessageId)
                            .collect(Collectors.toSet());
                    return receivedIds.containsAll(sentIds);
                },
                TIMEOUT,
                "all " + MESSAGE_COUNT + " burst-sent messages to be received by " + bob.getUsername());

        List<Message> finalState = bob.localMessages(conversationId).stream()
                .filter(m -> sentIds.contains(m.getMessageId()))
                .toList();

        assertEquals(MESSAGE_COUNT, finalState.size(),
                "no burst message should be lost or duplicated locally");
        assertEquals(MESSAGE_COUNT, finalState.stream().map(Message::getMessageId).distinct().count(),
                "burst messages must not appear as duplicate local rows");

        List<Message> expectedOrder = finalState.stream()
                .sorted(Comparator.comparingLong(Message::getLogicalTimestamp)
                        .thenComparing(Message::getMessageId))
                .toList();
        assertEquals(expectedOrder, finalState,
                "local repository must return burst messages ordered by (logical_timestamp, messageId)");
    }
}
