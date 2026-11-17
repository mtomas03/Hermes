package it.unibo.hermes.worker.configuration;

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
import it.unibo.hermes.worker.application.exception.PersistenceUnavailableException;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@EnableKafka
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${hermes.delivery.retry-backoff-ms:1000}")
    private long retryBackoffMs;
    @Value("${hermes.delivery.retry-max-backoff-ms:30000}")
    private long retryMaxBackoffMs;
    @Value("${hermes.delivery.stuck-alert-attempts:10}")
    private int stuckAlertAttempts;

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
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler deliveryErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(deliveryErrorHandler);
        return factory;
    }

    /**
     * Error handler for Worker consumers, with retry and backoff for persistence failures.
     */
    @Bean
    public DefaultErrorHandler deliveryErrorHandler() {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Poison record skipped - topic={} partition={} offset={}: {}",
                        record.topic(), record.partition(), record.offset(), exception.getMessage()),
                unlimitedCappedBackOff(retryBackoffMs, retryMaxBackoffMs));
        handler.defaultFalse(true);
        handler.addRetryableExceptions(PersistenceUnavailableException.class);
        handler.setRetryListeners((record, exception, deliveryAttempt) -> {
            if (ConsumerFailurePolicy.isStuckAlert(deliveryAttempt, stuckAlertAttempts)) {
                log.error("Consumer still failing after {} attempts, " +
                                "the partition is blocked until the message store database recovers " +
                                "- topic={} partition={} offset={}: {}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(),
                        exception.getMessage());
            } else {
                log.warn("Record processing failed (attempt {}), will retry " +
                                "- topic={} partition={} offset={}: {}",
                        deliveryAttempt, record.topic(), record.partition(), record.offset(),
                        exception.getMessage());
            }
        });
        return handler;
    }

    private ExponentialBackOff unlimitedCappedBackOff(long initialIntervalMs, long maxIntervalMs) {
        ExponentialBackOff backOff = new ExponentialBackOff(initialIntervalMs, 2.0);
        backOff.setMaxInterval(maxIntervalMs);
        backOff.setMaxElapsedTime(Long.MAX_VALUE);
        return backOff;
    }
}
