package it.unibo.hermes.e2e;

import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-End test suite for real-time one-to-one messaging.
 *
 * <p> Exercises the core messaging loop between two simultaneously connected online users.
 * Verifies that a message sent by A successfully traverses the entire distributed backbone
 * (Gateway -> Kafka MessageCreated -> Worker -> Cassandra -> Kafka DeliveryMessage -> Gateway -> STOMP)
 * and is durably persisted in B's local SQLite database, rather than merely being accepted by the ingress.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class RealTimeMessagingE2ETest {

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
    void deliversMessageBetweenTwoOnlineUsers() {
        String runId = TestIds.shortId();
        alice = new TestClient(TestIds.testId("sender"));
        bob = new TestClient(TestIds.testId("recipient"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);

        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String body = TestIds.messageBody(runId);

        String messageId = alice.sendMessageTo(bob.getUsername(), body);

        Message received = Poller.pollUntil(
                () -> bob.findLocalMessage(conversationId, messageId),
                TIMEOUT,
                "message " + messageId + " to be received and locally persisted by " + bob.getUsername());

        assertEquals(messageId, received.getMessageId());
        assertEquals(conversationId, received.getConversationId());
        assertEquals(alice.getUsername(), received.getSenderUsername());
        assertEquals(bob.getUsername(), received.getRecipientUsername());
        assertEquals(body, received.getContent());
    }
}
