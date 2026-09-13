package it.unibo.hermes.gateway.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Probe component to check Kafka broker health and reachability.
 */
@Component
public class KafkaHealthProbe {

    private static final Logger log = LoggerFactory.getLogger(KafkaHealthProbe.class);

    private final String bootstrapServers;

    public KafkaHealthProbe(@Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }

    /**
     * Checks Kafka broker reachability by probing metadata within a 2-second timeout window.
     *
     * @return {@code true} if reachable, {@code false} otherwise
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