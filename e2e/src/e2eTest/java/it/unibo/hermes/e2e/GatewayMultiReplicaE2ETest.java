package it.unibo.hermes.e2e;

import it.unibo.hermes.client.model.domain.Message;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End test suite for the Gateway multi-replicas.
 *
 * <p> Each Gateway hosts the WebSocket sessions of the clients that reached it through the Ingress, records
 * its own id in the Redis presence of those users and delivers only the {@code message-delivery} events
 * addressed to its id.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class GatewayMultiReplicaE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final Duration RECOVERY_TIMEOUT = TIMEOUT.multipliedBy(3);
    private static final String NAMESPACE = E2EConfig.get().namespace();

    private static final String GATEWAY_DEPLOYMENT = "gateway-deployment";
    private static final String GATEWAY_SELECTOR = "app=hermes-gateway";
    private static final int GATEWAYS = 3;
    private static final int MAX_CLIENTS = 30;
    private static final int MESSAGES_PER_DIRECTION = 2;

    private static final Pattern SESSION_ESTABLISHED = Pattern
            .compile("STOMP session established for user '([^']+)'");

    private final List<TestClient> clients = new ArrayList<>();

    private static Map<String, String> gatewayLogs(List<String> gateways) {
        Map<String, String> logs = new HashMap<>();
        gateways.forEach(pod -> logs.put(pod, Kubectl.podLogs(pod, NAMESPACE, Duration.ofSeconds(60))));
        return logs;
    }

    @AfterEach
    void tearDown() {
        for (TestClient client : clients) {
            client.close();
        }
        clients.clear();
    }

    @Test
    void messagesBetweenClientsOnDifferentGatewaysAreDeliveredByTheRecipientsGateway() {
        List<String> gateways = awaitGatewayPods();
        Map<String, String> podOf = connectClientsUntilEveryGatewayHostsOne(gateways);
        Map<String, List<TestClient>> byGateway = groupByGateway(podOf);

        List<Exchange> exchanges = new ArrayList<>();
        for (String from : gateways) {
            for (String to : gateways) {
                if (!from.equals(to)) {
                    for (int i = 0; i < MESSAGES_PER_DIRECTION; i++) {
                        exchanges.add(send(byGateway.get(from).getFirst(), byGateway.get(to).getFirst()));
                    }
                }
            }
        }
        exchanges.forEach(this::awaitDeliveredAndAcknowledged);

        // the delivery was done by only the recipient's Gateway
        Map<String, String> logs = gatewayLogs(gateways);
        for (Exchange ex : exchanges) {
            Set<String> deliveredBy = gateways.stream()
                    .filter(pod -> logs.get(pod).contains("Delivering message " + ex.messageId() + " via STOMP"))
                    .collect(Collectors.toSet());
            assertEquals(Set.of(podOf.get(ex.recipient().getUsername())), deliveredBy,
                    "message " + ex.messageId() + " from " + podOf.get(ex.sender().getUsername())
                            + " must be delivered by the Gateway hosting " + ex.recipient().getUsername());
        }
        assertExactlyOnce(exchanges);
    }

    @Test
    void lossOfOneGatewayLeavesTheOthersServingAndTheOrphanedClientsRecover() {
        List<String> gateways = awaitGatewayPods();
        Map<String, String> podOf = connectClientsUntilEveryGatewayHostsOne(gateways);
        Map<String, List<TestClient>> byGateway = groupByGateway(podOf);

        String victim = gateways.getFirst();
        TestClient orphan = byGateway.get(victim).getFirst();
        TestClient survivorA = byGateway.get(gateways.get(1)).getFirst();
        TestClient survivorB = byGateway.get(gateways.get(2)).getFirst();
        int orphanDisconnections = orphan.disconnectionCount();

        Kubectl.customKubectlCommand(Duration.ofSeconds(60), "-n", NAMESPACE, "delete", "pod", victim, "--wait=false");

        // clients on the surviving Gateways keep messaging, while the third one goes down
        Exchange amongSurvivors = send(survivorA, survivorB);
        awaitDeliveredAndAcknowledged(amongSurvivors);

        Poller.pollUntil(() -> orphan.disconnectionCount() > orphanDisconnections
                        ? Optional.of(true) : Optional.empty(),
                RECOVERY_TIMEOUT, Duration.ofMillis(20), "the orphaned client to observe session loss");
        Exchange duringBlackout = send(survivorA, orphan);

        Poller.pollUntilTrue(orphan::isConnected, RECOVERY_TIMEOUT,
                "the orphaned client to reconnect to a surviving or replacement Gateway");
        Poller.pollUntil(() -> orphan.syncAndPersist(duringBlackout.conversationId(), TIMEOUT).stream()
                        .filter(m -> m.messageId().equals(duringBlackout.messageId())).findFirst(),
                RECOVERY_TIMEOUT, "message " + duringBlackout.messageId() + " sent during the blackout to reach "
                        + orphan.getUsername());
        survivorA.awaitAcknowledged(duringBlackout.conversationId(), duringBlackout.messageId(), TIMEOUT);

        // on its new Gateway the client works in both directions, with the presence moved by its new session
        List<Exchange> afterRecovery = List.of(send(orphan, survivorB), send(survivorB, orphan));
        afterRecovery.forEach(this::awaitDeliveredAndAcknowledged);

        List<String> after = Kubectl.awaitReadyPods(GATEWAY_DEPLOYMENT, GATEWAY_SELECTOR, GATEWAYS, NAMESPACE,
                RECOVERY_TIMEOUT);
        assertFalse(after.contains(victim), "the deleted Pod " + victim + " must have been replaced; now: " + after);

        List<Exchange> all = new ArrayList<>(List.of(amongSurvivors, duringBlackout));
        all.addAll(afterRecovery);
        assertExactlyOnce(all);
    }

    private List<String> awaitGatewayPods() {
        return Kubectl.awaitReadyPods(GATEWAY_DEPLOYMENT, GATEWAY_SELECTOR, GATEWAYS, NAMESPACE, TIMEOUT);
    }

    private Map<String, String> connectClientsUntilEveryGatewayHostsOne(List<String> gateways) {
        Map<String, String> podOf = Map.of();
        while (new HashSet<>(podOf.values()).size() < gateways.size()) {
            assertTrue(clients.size() < MAX_CLIENTS, "after " + clients.size() + " clients the Gateways "
                    + gateways + " still do not all host one (placement: " + podOf + ")");
            for (int i = 0; i < GATEWAYS; i++) {
                TestClient client = new TestClient(TestIds.testId("gw" + clients.size()));
                client.registerAndLogin(TIMEOUT);
                client.connectAndAwait(TIMEOUT);
                clients.add(client);
            }
            podOf = hostingGateways(gateways);
        }
        return podOf;
    }

    private Map<String, String> hostingGateways(List<String> gateways) {
        Map<String, String> podOf = new HashMap<>();
        Map<String, String> logs = gatewayLogs(gateways);
        for (String pod : gateways) {
            SESSION_ESTABLISHED.matcher(logs.get(pod)).results().map(r -> r.group(1))
                    .forEach(username -> podOf.put(username, pod));
        }
        podOf.keySet().retainAll(clients.stream().map(TestClient::getUsername).collect(Collectors.toSet()));
        return podOf;
    }

    private Map<String, List<TestClient>> groupByGateway(Map<String, String> podOf) {
        return clients.stream().filter(c -> podOf.containsKey(c.getUsername()))
                .collect(Collectors.groupingBy(c -> podOf.get(c.getUsername())));
    }

    private Exchange send(TestClient sender, TestClient recipient) {
        String body = TestIds.messageBody(TestIds.shortId());
        String id = sender.sendMessageTo(recipient.getUsername(), body);
        return new Exchange(TestIds.conversationId(sender.getUsername(), recipient.getUsername()), id, body,
                sender, recipient);
    }

    private void awaitDeliveredAndAcknowledged(Exchange ex) {
        Message received = Poller.pollUntil(
                () -> ex.recipient().findLocalMessage(ex.conversationId(), ex.messageId()), TIMEOUT,
                "message " + ex.messageId() + " to reach " + ex.recipient().getUsername());
        assertEquals(ex.body(), received.getContent(), "delivered content must match what was sent");
        ex.sender().awaitAcknowledged(ex.conversationId(), ex.messageId(), TIMEOUT);
    }

    private void assertExactlyOnce(List<Exchange> exchanges) {
        Map<String, List<Exchange>> byConversation = exchanges.stream()
                .collect(Collectors.groupingBy(Exchange::conversationId));
        byConversation.forEach((conversationId, inConversation) -> {
            List<String> expected = inConversation.stream().map(Exchange::messageId).sorted().toList();
            for (TestClient client : List.of(inConversation.getFirst().sender(), inConversation.getFirst().recipient())) {
                assertEquals(expected, client.localMessages(conversationId).stream()
                                .map(Message::getMessageId).sorted().toList(),
                        client.getUsername() + " must hold each message of " + conversationId + " exactly once");
            }
        });
    }

    private record Exchange(String conversationId, String messageId, String body,
                            TestClient sender, TestClient recipient) {
    }
}