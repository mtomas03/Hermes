package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End test suite for WebSocket disconnection and reconnection.
 *
 * <p> Validates system resilience against temporary client disconnections. Unlike {@link FullSyncE2ETest},
 * this test forces an active WebSocket session to drop, sends a message when the session is down
 * and verifies that, upon reconnection and synchronisation, the client recovers the missed message. It ensures that
 * outdated presence or routing records from the terminated session do not permanently compromise message delivery.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class WebsocketReconnectE2ETest {

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
    void recoversMessageAfterClientDisconnectAndReconnect() {
        alice = new TestClient(TestIds.testId("reconnsender"));
        bob = new TestClient(TestIds.testId("reconnrecipient"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);

        assertTrue(bob.isConnected(), "precondition: bob must be connected before disconnecting");
        bob.disconnect();
        assertFalse(bob.isConnected(), "bob must actually be disconnected before A sends");

        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String body = TestIds.messageBody(TestIds.shortId());
        String messageId = alice.sendMessageTo(bob.getUsername(), body);

        Poller.pollUntil(() -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT), TIMEOUT,
                "message " + messageId + " to be durably stored while " + bob.getUsername() + " is disconnected");

        bob.connectAndAwait(TIMEOUT);
        assertTrue(bob.isConnected(), "bob must be able to reconnect");

        InboundMessageDto synced = Poller.pollUntil(
                () -> bob.syncAndPersist(conversationId, TIMEOUT).stream()
                        .filter(m -> m.messageId().equals(messageId))
                        .findFirst(),
                TIMEOUT,
                Duration.ofSeconds(1),
                "message " + messageId + " to be recovered by " + bob.getUsername() + " via sync after reconnect");

        assertEquals(body, synced.content());
        assertTrue(bob.findLocalMessage(conversationId, messageId).isPresent(),
                "recovered message must be persisted locally after reconnect+sync");
    }

    /**
     * Deterministic sender-side counterpart of the test above: the sender itself is offline when it composes
     * the message. The message must be kept in the local outbox and retransmitted, with the same id, when
     * the sender reconnects; the server then accepts it (the sender's copy becomes SENT) and the recipient
     * gets exactly one copy.
     */
    @Test
    void messageComposedWhileTheSenderIsOfflineIsRetransmittedAfterItReconnects() {
        alice = new TestClient(TestIds.testId("outboxsender"));
        bob = new TestClient(TestIds.testId("outboxrecipient"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);

        alice.disconnect();
        assertFalse(alice.isConnected(), "alice must actually be disconnected when she composes the message");
        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String body = TestIds.messageBody(TestIds.shortId());
        String messageId = alice.sendMessageTo(bob.getUsername(), body);

        assertEquals(MessageStatus.PENDING, alice.findLocalMessage(conversationId, messageId).orElseThrow().getStatus(),
                "while offline the message must wait in the outbox, not be reported as sent");

        alice.connectAndAwait(TIMEOUT);

        Poller.pollUntil(() -> alice.findLocalMessage(conversationId, messageId)
                        .filter(m -> m.getStatus() == MessageStatus.SENT),
                TIMEOUT, "message " + messageId + " to be retransmitted from the outbox and accepted by the server");
        InboundMessageDto delivered = Poller.pollUntil(
                () -> bob.syncAndPersist(conversationId, TIMEOUT).stream()
                        .filter(m -> m.messageId().equals(messageId)).findFirst(),
                TIMEOUT, "message " + messageId + " to reach " + bob.getUsername());

        assertEquals(body, delivered.content());
        assertEquals(1, bob.countLocalCopies(conversationId, messageId), "no duplicate in the recipient's history");
        assertEquals(1, alice.countLocalCopies(conversationId, messageId), "no duplicate in the sender's history");
    }
}
