package it.unibo.hermes.gateway.infrastructure.adapter.secondary.producer;

import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Utility class for sending messages to Kafka and waiting for the broker's confirmation.
 */
public final class KafkaSend {

    private KafkaSend() {
    }

    /**
     * Sends a record and waits for the broker's acknowledgement.
     *
     * @param template    the Kafka template
     * @param topic       the destination topic
     * @param key         the record key
     * @param payload     the record value
     * @param timeout     the max time to wait for the broker's confirmation
     * @param description the failure message
     * @throws BackboneUnavailableException if the broker did not confirm the record
     *                                      or if the send operation was interrupted
     */
    public static void sendAndAwait(
            KafkaTemplate<String, String> template, String topic, String key,
            String payload, Duration timeout, String description) {
        long start = System.currentTimeMillis();
        try {
            CompletableFuture<?> future = template.send(topic, key, payload);
            long remaining = timeout.toMillis() - (System.currentTimeMillis() - start);
            future.get(Math.max(remaining, 1L), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new BackboneUnavailableException(description, e, true);
        } catch (ExecutionException e) {
            boolean unknown = e.getCause() instanceof org.apache.kafka.common.errors.TimeoutException;
            throw new BackboneUnavailableException(description, e, unknown);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackboneUnavailableException(description, e, true);
        } catch (RuntimeException e) {
            throw new BackboneUnavailableException(description, e);
        }
    }
}
