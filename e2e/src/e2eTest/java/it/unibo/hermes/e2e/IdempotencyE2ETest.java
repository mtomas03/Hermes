package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-End test suite to verify the idempotence of the message pipeline.
 *
 * <p> Re-sending the same message or sending a new ACK must be safe:
 * no duplicate rows on either the server side or locally
 * and the delivery status on the server side must never revert.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class IdempotencyE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
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
    void duplicateAcknowledgementsAreSafe() {
        connectPair();
        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());

        String first = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("dupack1"));
        awaitAcknowledged(conversationId, first);

        bob.sendAck(first);
        bob.sendAck(first);
        bob.sendAck(first);

        String second = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("dupack2"));
        awaitAcknowledged(conversationId, second);

        List<InboundMessageDto> synced = alice.syncConversation(conversationId, TIMEOUT).messages();
        assertEquals(1, count(synced, first),
                "the duplicated ACK must not duplicate the message server-side");
        assertEquals(ACKNOWLEDGED, statusOf(synced, first),
                "the server-side state must stay ACKNOWLEDGED after duplicate ACKs");
        assertEquals(1, bob.localMessages(conversationId).stream()
                .filter(m -> m.getMessageId().equals(first)).count());
    }

    @Test
    void resubmittingTheSameMessageIdDoesNotDuplicateOrRevertIt() {
        connectPair();
        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());

        String original = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("resubmit"));
        awaitAcknowledged(conversationId, original);

        alice.resubmitMessage(conversationId, original);
        alice.resubmitMessage(conversationId, original);

        String barrier = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("resubmitBarrier"));
        awaitAcknowledged(conversationId, barrier);

        List<InboundMessageDto> synced = alice.syncConversation(conversationId, TIMEOUT).messages();
        assertEquals(1, count(synced, original),
                "a re-submitted message_id must exist exactly once server-side");
        assertEquals(ACKNOWLEDGED, statusOf(synced, original),
                "reprocessing a re-submission must not reset an acknowledged message");
        List<Message> local = bob.localMessages(conversationId);
        assertEquals(1, local.stream().filter(m -> m.getMessageId().equals(original)).count(),
                "the recipient must hold the message exactly once");
        assertEquals(2, synced.size(),
                "only the original and the barrier message exist in the conversation");
    }

    private void connectPair() {
        alice = new TestClient(TestIds.testId("idemA"));
        bob = new TestClient(TestIds.testId("idemB"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);
    }

    private void awaitAcknowledged(String conversationId, String messageId) {
        Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT)
                        .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                TIMEOUT,
                "server-side state of message " + messageId + " to become ACKNOWLEDGED");
    }

    private static long count(List<InboundMessageDto> messages, String messageId) {
        return messages.stream().filter(m -> m.messageId().equals(messageId)).count();
    }

    private static String statusOf(List<InboundMessageDto> messages, String messageId) {
        return messages.stream().filter(m -> m.messageId().equals(messageId))
                .map(InboundMessageDto::messageStatus).findFirst().orElse(null);
    }
}
