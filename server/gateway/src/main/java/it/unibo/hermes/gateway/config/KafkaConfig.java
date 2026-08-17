package it.unibo.hermes.gateway.config;

import it.unibo.hermes.gateway.event.MessageEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Spring configuration class defining the Kafka template and producer properties for publishing message events.
 */
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Creates the Kafka producer factory configured with serialization settings and failure threshold limits.
     *
     * @return the configured producer factory for string keys and message event payloads
     */
    @Bean
    public ProducerFactory<String, MessageEvent> messageEventProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 0);
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 5_000);
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3_000);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 3_000);
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates the Kafka template for publishing message events.
     *
     * @return the Kafka template
     */
    @Bean
    public KafkaTemplate<String, MessageEvent> kafkaTemplate() {
        return new KafkaTemplate<>(messageEventProducerFactory());
    }
}