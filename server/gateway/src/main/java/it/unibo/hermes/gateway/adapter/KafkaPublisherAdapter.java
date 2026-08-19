package it.unibo.hermes.gateway.adapter;

import it.unibo.hermes.gateway.event.MessageEvent;
import it.unibo.hermes.gateway.exception.BackboneUnavailableException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Kafka adapter handling message event publishing and broker connectivity checks.
 */
@Component
public class KafkaPublisherAdapter {

    private static final Logger log = LoggerFactory.getLogger(KafkaPublisherAdapter.class);

    private final KafkaTemplate<String, MessageEvent> kafkaTemplate;

    @Value("${hermes.kafka.topic-messages:hermes-messages}")
    private String topic;

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Creates the adapter with the configured {@link KafkaTemplate}.
     *
     * @param kafkaTemplate the template used to publish message events
     */
    public KafkaPublisherAdapter(KafkaTemplate<String, MessageEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Synchronously publishes a message event to Kafka, partitioning by conversation ID to preserve message ordering.
     *
     * @param event the message event to publish
     * @throws BackboneUnavailableException if the broker is unreachable or fails to acknowledge within the timeout
     */
    public void publish(MessageEvent event) {
        try {
            kafkaTemplate.send(topic, event.getConversationId(), event).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new BackboneUnavailableException(
                    "Failed to publish message " + event.getMessageId() + " to Kafka", e);
        }
    }

    /**
     * Checks Kafka broker reachability by attempting to fetch topic metadata within a short timeout window.
     *
     * @return {@code true} if the broker is reachable, {@code false} otherwise
     */
    public boolean isHealthy() {
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "2000");
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "2000");

        try (AdminClient admin = AdminClient.create(props)) {
            admin.listTopics().names().get(2, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            log.debug("Kafka health probe failed: {}", e.getMessage());
            return false;
        }
    }
}