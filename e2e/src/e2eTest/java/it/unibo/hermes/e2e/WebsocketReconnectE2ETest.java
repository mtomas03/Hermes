package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebSocket disconnect / reconnect test.
 *
 * <p> Unlike {@link FullSyncE2ETest}, this test scenario connects B first,
 * disconnects it explicitly, sends while disconnected, then reconnects and synchronises.
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
}
