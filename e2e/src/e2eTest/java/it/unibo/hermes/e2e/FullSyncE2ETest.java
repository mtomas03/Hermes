package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full synchronisation test.
 *
 * <p> B never connects over STOMP while A's message is sent. The message must still survive
 * and once B does connect and performs the full synchronisation, it must recover the message exactly once.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class FullSyncE2ETest {

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
    void recoversOfflineMessageThroughFullSynchronization() {
        alice = new TestClient(TestIds.testId("offlinesender"));
        bob = new TestClient(TestIds.testId("offlinerecipient"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);

        alice.connectAndAwait(TIMEOUT);

        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String body = TestIds.messageBody(TestIds.shortId());
        String messageId = alice.sendMessageTo(bob.getUsername(), body);

        InboundMessageDto persisted = Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT),
                TIMEOUT,
                "message " + messageId + " to be durably persisted server-side while " + bob.getUsername()
                        + " is offline");
        assertEquals(body, persisted.content());

        bob.connectAndAwait(TIMEOUT);
        List<InboundMessageDto> synced = bob.syncAndPersist(conversationId, TIMEOUT);

        assertTrue(synced.stream().anyMatch(m -> m.messageId().equals(messageId)),
                "Full Sync response must include the message sent while offline");

        Optional<Message> local = bob.findLocalMessage(conversationId, messageId);
        assertTrue(local.isPresent(), "the offline message must be persisted locally after sync");
        assertEquals(body, local.get().getContent());

        long occurrences = bob.localMessages(conversationId).stream()
                .filter(m -> m.getMessageId().equals(messageId))
                .count();
        assertEquals(1, occurrences, "the synced message must not appear duplicated locally");
    }
}
