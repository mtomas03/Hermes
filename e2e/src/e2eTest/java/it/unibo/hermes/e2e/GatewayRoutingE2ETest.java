package it.unibo.hermes.e2e;

import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Gateway Routing test
 *
 * <p> The load balancer decides, non-deterministically, which replica actually serves
 * each of A's and B's connections. Connecting several independent client pairs increases the empirical
 * chance that at least one pair is served by two different replicas.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class GatewayRoutingE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final int PAIR_COUNT = 3;

    private final List<TestClient> clients = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (TestClient client : clients) {
            client.close();
        }
        clients.clear();
    }

    @Test
    void deliversMessagesThroughThePublicEntryPointRegardlessOfGatewayReplica() {
        for (int pair = 0; pair < PAIR_COUNT; pair++) {
            TestClient sender = register(TestIds.testId("route-a" + pair));
            TestClient recipient = register(TestIds.testId("route-b" + pair));

            sender.connectAndAwait(TIMEOUT);
            recipient.connectAndAwait(TIMEOUT);

            String conversationId = TestIds.conversationId(sender.getUsername(), recipient.getUsername());
            String body = TestIds.messageBody(TestIds.shortId());
            String messageId = sender.sendMessageTo(recipient.getUsername(), body);

            Message received = Poller.pollUntil(
                    () -> recipient.findLocalMessage(conversationId, messageId),
                    TIMEOUT,
                    "pair #" + pair + ": message " + messageId + " to reach " + recipient.getUsername()
                            + " through the public entry point, whichever Gateway replica served it");

            assertEquals(body, received.getContent(),
                    "pair #" + pair + ": delivered content must match what was sent");
        }
    }

    private TestClient register(String username) {
        TestClient client = new TestClient(username);
        client.registerAndLogin(TIMEOUT);
        clients.add(client);
        return client;
    }
}
