package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-End test suite for Kafka unavailability and recovery.
 *
 * <p> When Kafka is down, the Gateway cannot send messages to the Worker:
 * the Gateway persists the message directly in Cassandra (fallback path) so it is not
 * lost but nothing delivers it in real time. After Kafka is back, the message is recovered through full
 * synchronisation and the normal pipeline works again.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class KafkaOutageRecoveryE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final Duration KAFKA_TIMEOUT = Duration.ofSeconds(180);
    private static final String ACKNOWLEDGED = "ACKNOWLEDGED";
    private static final String STATEFUL_SET = "kafka-statefulset";
    private static final String APP_LABEL = "hermes-kafka";

    private TestClient alice;
    private TestClient bob;
    private boolean kafkaStopped;

    @AfterEach
    void tearDown() {
        try {
            if (kafkaStopped) {
                // never leave the shared cluster without its broker, even if the test failed midway
                Kubectl.scaleStatefulSetAndAwait(STATEFUL_SET, APP_LABEL, 1,
                        E2EConfig.get().namespace(), KAFKA_TIMEOUT);
            }
        } finally {
            if (alice != null) {
                alice.close();
            }
            if (bob != null) {
                bob.close();
            }
        }
    }

    @Test
    void messageSentWhileKafkaIsDownIsPersistedAndRecoveredAfterKafkaReturns() {
        alice = new TestClient(TestIds.testId("kafkaA"));
        bob = new TestClient(TestIds.testId("kafkaB"));
        alice.registerAndLogin(TIMEOUT);
        bob.registerAndLogin(TIMEOUT);
        alice.connectAndAwait(TIMEOUT);
        bob.connectAndAwait(TIMEOUT);

        String conversationId = TestIds.conversationId(alice.getUsername(), bob.getUsername());
        Kubectl.scaleStatefulSetAndAwait(STATEFUL_SET, APP_LABEL, 0, E2EConfig.get().namespace(), KAFKA_TIMEOUT);
        kafkaStopped = true;
        String body = TestIds.messageBody("duringOutage");
        String messageId = alice.sendMessageTo(bob.getUsername(), body);

        InboundMessageDto persisted = Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, messageId, TIMEOUT), KAFKA_TIMEOUT,
                "message " + messageId + " to be persisted server-side while Kafka is down");
        assertEquals(body, persisted.content());
        assertNotEquals(ACKNOWLEDGED, persisted.messageStatus(), "nobody could have acknowledged it yet");

        assertTrue(bob.findLocalMessage(conversationId, messageId).isEmpty(),
                "no real-time delivery is possible while Kafka is down");

        Kubectl.scaleStatefulSetAndAwait(STATEFUL_SET, APP_LABEL, 1, E2EConfig.get().namespace(), KAFKA_TIMEOUT);
        kafkaStopped = false;

        Poller.pollUntil(
                () -> bob.syncAndPersist(conversationId, TIMEOUT).stream()
                        .filter(m -> m.messageId().equals(messageId)).findFirst(),
                KAFKA_TIMEOUT, "message " + messageId + " to be recovered by " + bob.getUsername()
                        + " through full synchronization");
        assertTrue(bob.findLocalMessage(conversationId, messageId).isPresent());

        String afterId = alice.sendMessageTo(bob.getUsername(), TestIds.messageBody("afterRecovery"));
        Poller.pollUntil(() -> bob.findLocalMessage(conversationId, afterId), KAFKA_TIMEOUT,
                "real-time delivery of " + afterId + " after Kafka recovered");
        Poller.pollUntil(
                () -> alice.findRemoteMessage(conversationId, afterId, TIMEOUT)
                        .filter(m -> ACKNOWLEDGED.equals(m.messageStatus())),
                KAFKA_TIMEOUT, "message " + afterId + " to be ACKNOWLEDGED after Kafka recovered");
    }
}
