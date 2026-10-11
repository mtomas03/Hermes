package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-End test suite for message delivery acknowledgements.
 *
 * <p> Verifies the full acknowledgement lifecycle. Ensures that when a recipient (B)
 * receives and locally persists a message via STOMP, the Client correctly generates a Delivery ACK.
 * This ACK must be ingested by the Gateway as a Kafka {@code MessageAck} event tied to the
 * authenticated recipient's identity, consumed by the Worker, and finally persisted in Cassandra,
 * transitioning the server-side state to {@code ACKNOWLEDGED}.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class DeliveryAckE2ETest {

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
    void acknowledgesDeliveredMessageAndUpdatesServerSideState() {
        alice = new TestClient(TestIds.testId("acksender"));
        bob = new TestClient(TestIds.testId("ackrecipient"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);

        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String body = TestIds.messageBody(TestIds.shortId());
        String messageId = alice.sendMessageTo(bob.getUsername(), body);

        Poller.pollUntil(() -> bob.findLocalMessage(conversationId, messageId), TIMEOUT,
                "message " + messageId + " to reach " + bob.getUsername() + " before it can be ACKed");

        InboundMessageDto acknowledged = Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT),
                TIMEOUT,
                "server-side delivery state of message " + messageId + " to become " + ACKNOWLEDGED);

        assertEquals(ACKNOWLEDGED, acknowledged.messageStatus(),
                "MessageCreated/DeliveryMessage alone must not be mistaken for a completed ACK");
    }

    @Test
    void duplicateSynchronizationDoesNotCreateDuplicateLocalMessages() {
        alice = new TestClient(TestIds.testId("dupsyncsender"));
        bob = new TestClient(TestIds.testId("dupsyncrecipient"));

        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);

        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        String messageId = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody(TestIds.shortId()));

        Poller.pollUntil(() -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT)
                        .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                TIMEOUT, "message " + messageId + " to be delivered and ACKed before duplicate-sync check");

        bob.syncAndPersist(conversationId, TIMEOUT);
        bob.syncAndPersist(conversationId, TIMEOUT);

        long occurrences = bob.localMessages(conversationId).stream()
                .filter(m -> m.getMessageId().equals(messageId))
                .count();
        assertEquals(1, occurrences,
                "applying the same sync result twice must not duplicate the local message row");
    }
}
