package it.unibo.hermes.e2e;

import it.unibo.hermes.client.dto.InboundMessageDto;
import it.unibo.hermes.client.model.domain.MessageStatus;
import it.unibo.hermes.e2e.support.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End test suite for the Worker multi-replicas.
 *
 * <p> The Workers share one Kafka consumer group, so Kafka splits the partitions of {@code message-created}
 * (key: conversationId) and {@code message-acknowledged} (key: messageId) among them. The delivery of a
 * message and its ACK are usually handled by different Workers at the same time. This suite sends
 * many independent messages through the normal application path and checks, using only what the clients can observe
 * (delivery, synchronisation, ACK states), that the state stays correct.
 */
@ExtendWith({MinikubeReadiness.class, DiagnosticsCollector.class})
class WorkerMultiReplicaE2ETest {

    private static final Duration TIMEOUT = E2EConfig.get().defaultTimeout();
    private static final Duration LOAD_TIMEOUT = TIMEOUT.multipliedBy(3);
    private static final Duration KUBECTL_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration SLOW_POLL = Duration.ofSeconds(3);
    private static final String NAMESPACE = E2EConfig.get().namespace();
    private static final String WORKER_DEPLOYMENT = "worker-deployment";
    private static final String WORKER_SELECTOR = "app=hermes-worker";
    private static final int EXPECTED_REPLICAS = 3;
    private static final String KAFKA_POD = "kafka-statefulset-0";
    private static final String KAFKA_BIN = "/opt/kafka/bin";
    private static final String CREATED_TOPIC = "message-created";
    private static final String ACK_TOPIC = "message-acknowledged";
    private static final String WORKER_GROUP = "hermes-worker";
    private static final Pattern FORWARDED = Pattern.compile("Message (\\S+) forwarded to gateway");
    private static final Pattern STORED = Pattern.compile("message (\\S+) stored for sync");
    private static final Pattern FALLBACK = Pattern.compile("Failed to forward message (\\S+) to gateway");
    private static final Pattern ACK_RECEIVED = Pattern.compile("Processing ACK for message (\\S+) from");
    private static final Pattern ACK_DONE = Pattern.compile("Message (\\S+) marked as ACKNOWLEDGED");

    private final List<TestClient> clients = new ArrayList<>();

    private static Map<String, List<SentMessage>> byConversation(List<SentMessage> messages) {
        return messages.stream().collect(Collectors.groupingBy(SentMessage::conversationId,
                TreeMap::new, Collectors.toList()));
    }

    private static String summarize(Map<String, PodActivity> activity) {
        return activity.values().stream()
                .map(a -> "  " + a.pod() + ": first-time deliveries=" + a.delivered().size()
                        + ", ACKs processed=" + a.acked().size())
                .collect(Collectors.joining("\n", "Worker activity on the test messages:\n", ""));
    }

    private static String kubectl(String... args) {
        return Kubectl.customKubectlCommand(KUBECTL_TIMEOUT, args);
    }

    @AfterEach
    void tearDown() {
        for (TestClient client : clients) {
            client.close();
        }
        clients.clear();
    }

    @Test
    void threeConcurrentWorkersShareTheLoadWithoutCompromisingMessageState() {
        List<String> workerPods = Kubectl.awaitReadyPods(
                WORKER_DEPLOYMENT, WORKER_SELECTOR, EXPECTED_REPLICAS, NAMESPACE, TIMEOUT);

        List<TestClient> senders = register("mrsend", 4);
        List<TestClient> recipients = register("mrrecv", 6);
        // 4 x 6 distinct conversations, 2 messages each; every 6th message is also re-submitted
        List<SentMessage> messages = sendConcurrently(senders, recipients, 2, 6, new CountDownLatch(1));
        Set<String> ids = messages.stream().map(SentMessage::messageId).collect(Collectors.toSet());
        assertEquals(senders.size() * recipients.size() * 2, ids.size(), "every message must have a unique id");

        awaitDelivered(messages);
        awaitAcknowledged(messages);
        assertConsistentState(messages);

        // duplicate ACKs, sent again after the state is final: they must not change anything
        List<SentMessage> ackedAgain = new ArrayList<>();
        for (int i = 0; i < messages.size(); i += 5) {
            ackedAgain.add(messages.get(i));
        }
        Map<String, Long> ackBaseline = ackReceiptCounts(workerPods, ids);
        ackedAgain.forEach(m -> m.recipient().sendAck(m.messageId()));
        Poller.pollUntil(() -> {
                    Map<String, Long> now = ackReceiptCounts(workerPods, ids);
                    return ackedAgain.stream().allMatch(m ->
                            now.getOrDefault(m.messageId(), 0L) > ackBaseline.getOrDefault(m.messageId(), 0L))
                            ? Optional.of(true) : Optional.empty();
                },
                LOAD_TIMEOUT, SLOW_POLL,
                "the Workers to process the duplicate ACKs (their logs show one more ACK receipt per message)");
        assertConsistentState(messages);

        assertKafkaPartitions();
        Map<String, PodActivity> activity = collectActivity(workerPods, ids);
        String summary = summarize(activity);
        System.out.println(summary);
        assertWorkDistributed(activity, ids, summary);
    }

    @Test
    void messagesInFlightSurviveTheLossOfOneWorkerReplica() {
        List<String> workerPods = Kubectl.awaitReadyPods(
                WORKER_DEPLOYMENT, WORKER_SELECTOR, EXPECTED_REPLICAS, NAMESPACE, TIMEOUT);
        String victim = workerPods.getFirst();

        List<TestClient> senders = register("mrfsend", 2);
        List<TestClient> recipients = register("mrfrecv", 3);
        CountDownLatch firstSent = new CountDownLatch(1);
        CompletableFuture<List<SentMessage>> burst = CompletableFuture.supplyAsync(
                () -> sendConcurrently(senders, recipients, 4, 0, firstSent));

        // the Worker Pod is deleted while the burst is being sent and processed; the ReplicaSet replaces it
        awaitLatch(firstSent);
        Kubectl.customKubectlCommand(KUBECTL_TIMEOUT, "-n", NAMESPACE, "delete", "pod", victim, "--wait=false");
        List<SentMessage> messages = burst.orTimeout(LOAD_TIMEOUT.toSeconds(), TimeUnit.SECONDS).join();

        // everything is delivered, ends ACKNOWLEDGED once and the group has rebalanced and recovered
        awaitDelivered(messages);
        awaitAcknowledged(messages);
        assertConsistentState(messages);

        List<String> podsAfter = Kubectl.awaitReadyPods(
                WORKER_DEPLOYMENT, WORKER_SELECTOR, EXPECTED_REPLICAS, NAMESPACE, TIMEOUT);
        assertFalse(podsAfter.contains(victim), "the deleted Pod " + victim + " must have been replaced; now: "
                + podsAfter);
        assertEquals(EXPECTED_REPLICAS, podsAfter.size());
    }

    private void assertKafkaPartitions() {
        for (String topic : List.of(CREATED_TOPIC, ACK_TOPIC)) {
            String out = kubectl("-n", NAMESPACE, "exec", KAFKA_POD, "--", KAFKA_BIN + "/kafka-topics.sh",
                    "--bootstrap-server", "localhost:9092", "--describe", "--topic", topic);
            Matcher m = Pattern.compile("PartitionCount:\\s*(\\d+)").matcher(out);
            assertTrue(m.find(), "cannot read the partition count of " + topic + " from:\n" + out);
            int partitions = Integer.parseInt(m.group(1));
            assertTrue(partitions >= EXPECTED_REPLICAS, "Topic " + topic + " has " + partitions
                    + " partition(s): a consumer group can use at most one consumer per partition.");
        }
    }

    private List<TestClient> register(String role, int count) {
        List<TestClient> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TestClient client = new TestClient(TestIds.testId(role + i));
            client.registerAndLogin(TIMEOUT);
            client.connectAndAwait(TIMEOUT);
            clients.add(client);
            created.add(client);
        }
        return created;
    }

    private List<SentMessage> sendConcurrently(List<TestClient> senders, List<TestClient> recipients,
                                               int perPair, int resubmitEvery, CountDownLatch firstSent) {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(senders.size())) {
            List<Future<List<SentMessage>>> futures = new ArrayList<>();
            for (TestClient sender : senders) {
                futures.add(pool.submit(() -> {
                    start.await();
                    List<SentMessage> mine = new ArrayList<>();
                    for (int round = 0; round < perPair; round++) {
                        for (TestClient recipient : recipients) {
                            String body = TestIds.messageBody(TestIds.shortId());
                            String id = sender.sendMessageTo(recipient.getUsername(), body);
                            String conversationId = TestIds.conversationId(sender.getUsername(),
                                    recipient.getUsername());
                            boolean again = resubmitEvery > 0 && mine.size() % resubmitEvery == resubmitEvery - 1;
                            if (again) {
                                sender.resubmitMessage(conversationId, id);
                            }
                            mine.add(new SentMessage(conversationId, id, body, sender, recipient));
                            firstSent.countDown();
                        }
                    }
                    return mine;
                }));
            }
            start.countDown();
            List<SentMessage> all = new ArrayList<>();
            for (Future<List<SentMessage>> future : futures) {
                all.addAll(future.get(LOAD_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
            }
            return all;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while sending the load", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError("Sending the load failed", e);
        }
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(LOAD_TIMEOUT.toSeconds(), TimeUnit.SECONDS),
                    "Timed out waiting for the first message of the burst to be sent");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for the first message of the burst to be sent", e);
        }
    }

    private void awaitDelivered(List<SentMessage> messages) {
        for (SentMessage m : messages) {
            Poller.pollUntil(() -> m.recipient().findLocalMessage(m.conversationId(), m.messageId()), LOAD_TIMEOUT,
                    "message " + m.messageId() + " to reach " + m.recipient().getUsername());
        }
    }

    private void awaitAcknowledged(List<SentMessage> messages) {
        for (SentMessage m : messages) {
            m.sender().awaitAcknowledged(m.conversationId(), m.messageId(), LOAD_TIMEOUT);
        }
    }

    private void assertConsistentState(List<SentMessage> messages) {
        for (Map.Entry<String, List<SentMessage>> conversation : byConversation(messages).entrySet()) {
            String conversationId = conversation.getKey();
            List<SentMessage> expected = conversation.getValue();
            List<String> expectedIds = expected.stream().map(SentMessage::messageId).sorted().toList();
            SentMessage first = expected.getFirst();

            List<InboundMessageDto> server = first.sender().syncConversation(conversationId, TIMEOUT).messages();
            assertEquals(expectedIds, server.stream().map(InboundMessageDto::messageId).sorted().toList(),
                    "server history of " + conversationId + " must hold each message exactly once");
            Map<String, InboundMessageDto> serverById = server.stream()
                    .collect(Collectors.toMap(InboundMessageDto::messageId, d -> d));
            for (SentMessage m : expected) {
                assertEquals(m.body(), serverById.get(m.messageId()).content(),
                        "content of " + m.messageId() + " must survive concurrent processing");
                assertEquals(MessageStatus.ACKNOWLEDGED.name(), serverById.get(m.messageId()).messageStatus(),
                        "status of " + m.messageId() + " must not regress");
            }
            for (TestClient owner : List.of(first.sender(), first.recipient())) {
                assertEquals(expectedIds, owner.localMessages(conversationId).stream()
                                .map(it.unibo.hermes.client.model.domain.Message::getMessageId).sorted().toList(),
                        owner.getUsername() + " must have each message of " + conversationId + " exactly once");
            }
        }
    }

    private Map<String, Long> ackReceiptCounts(List<String> pods, Set<String> ids) {
        Map<String, Long> counts = new HashMap<>();
        collectActivity(pods, ids).values()
                .forEach(a -> a.ackReceipts().forEach((id, n) -> counts.merge(id, n, Long::sum)));
        return counts;
    }

    private Map<String, PodActivity> collectActivity(List<String> pods, Set<String> ids) {
        Map<String, PodActivity> result = new TreeMap<>();
        for (String pod : pods) {
            result.put(pod, PodActivity.of(pod, Kubectl.podLogs(pod, NAMESPACE, KUBECTL_TIMEOUT), ids));
        }
        return result;
    }

    private void assertWorkDistributed(Map<String, PodActivity> activity, Set<String> ids, String summary) {
        String details = summary + "\nConsumer group:\n" + groupDescription();
        Collection<PodActivity> pods = activity.values();

        assertTrue(pods.stream().filter(a -> !a.delivered().isEmpty()).count() >= 2,
                "message-created events were handled by fewer than 2 Workers\n" + details);
        assertTrue(pods.stream().filter(a -> !a.acked().isEmpty()).count() >= 2,
                "message-acknowledged events were handled by fewer than 2 Workers\n" + details);
        assertTrue(pods.stream().noneMatch(a -> a.delivered().isEmpty() && a.acked().isEmpty()),
                "a Worker replica processed none of the test's events (idle replica)\n" + details);

        boolean crossWorker = ids.stream().anyMatch(id -> pods.stream().anyMatch(delivering ->
                delivering.delivered().contains(id) && pods.stream().anyMatch(acking ->
                        acking.acked().contains(id) && !acking.pod().equals(delivering.pod()))));
        assertTrue(crossWorker, "no message had its delivery and its ACK handled by different Workers\n" + details);
    }

    private String groupDescription() {
        try {
            return kubectl("-n", NAMESPACE, "exec", KAFKA_POD, "--", KAFKA_BIN + "/kafka-consumer-groups.sh",
                    "--bootstrap-server", "localhost:9092", "--describe", "--group", WORKER_GROUP);
        } catch (AssertionError e) {
            return "(unavailable: " + e.getMessage() + ")";
        }
    }

    private record SentMessage(String conversationId, String messageId, String body,
                               TestClient sender, TestClient recipient) {
    }

    private record PodActivity(String pod, Set<String> delivered, Set<String> acked, Map<String, Long> ackReceipts) {

        static PodActivity of(String pod, String log, Set<String> ids) {
            Set<String> delivered = new HashSet<>();
            for (Pattern completion : List.of(FORWARDED, STORED, FALLBACK)) {
                delivered.addAll(idsIn(log, completion, ids));
            }
            return new PodActivity(pod, delivered, new HashSet<>(idsIn(log, ACK_DONE, ids)),
                    idsIn(log, ACK_RECEIVED, ids).stream()
                            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
        }

        private static List<String> idsIn(String log, Pattern pattern, Set<String> ids) {
            return pattern.matcher(log).results().map(r -> r.group(1)).filter(ids::contains).toList();
        }
    }
}