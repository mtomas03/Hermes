package it.unibo.hermes.worker.config;

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

    @Value("${hermes.delivery.max-retry-attempts:3}")
    private long maxRetryAttempts;

    @Value("${hermes.delivery.retry-backoff-ms:1000}")
    private long retryBackoffMs;

    /**
     * KafkaTemplate for Worker producers.
     */
    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Configures the KafkaListenerContainerFactory for Worker consumers.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(deliveryErrorHandler());
        return factory;
    }

    /**
     * Error handler for Worker listener execution failures.
     */
    @Bean
    public DefaultErrorHandler deliveryErrorHandler() {
        FixedBackOff backOff = new FixedBackOff(retryBackoffMs, maxRetryAttempts);
        return new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Worker consumer failed after {} retries - topic={} partition={} offset={}: {}",
                        maxRetryAttempts, record.topic(), record.partition(), record.offset(), exception.getMessage()),
                backOff);
    }
}
