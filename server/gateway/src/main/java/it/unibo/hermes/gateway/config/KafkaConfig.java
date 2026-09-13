package it.unibo.hermes.gateway.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${hermes.kafka.retry.max-attempts:3}")
    private long maxRetryAttempts;

    @Value("${hermes.kafka.retry.initial-backoff-ms:1000}")
    private long retryBackoffMs;

    /**
     * KafkaTemplate for Gateway producers.
     */
    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Configures the KafkaListenerContainerFactory for Gateway consumers.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(gatewayErrorHandler());
        return factory;
    }

    /**
     * Error handler for Gateway listener execution failures.
     */
    @Bean
    public DefaultErrorHandler gatewayErrorHandler() {
        FixedBackOff backOff = new FixedBackOff(retryBackoffMs, maxRetryAttempts);
        return new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Gateway consumer failed after {} retries - topic={} partition={} offset={}: {}",
                        maxRetryAttempts, record.topic(), record.partition(), record.offset(), exception.getMessage()),
                backOff);
    }
}
