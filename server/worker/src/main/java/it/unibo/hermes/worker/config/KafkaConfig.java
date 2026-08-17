package it.unibo.hermes.worker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka consumer and error-handler configuration.
 *
 * <p> Retry policy: fixed backoff with a bounded number of attempts.
 * When all retries are exhausted the record is logged and skipped.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    @Value("${hermes.delivery.max-retry-attempts:3}")
    private long maxRetryAttempts;

    @Value("${hermes.delivery.retry-backoff-ms:1000}")
    private long retryBackoffMs;

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(deliveryErrorHandler());
        return factory;
    }

    @Bean
    public DefaultErrorHandler deliveryErrorHandler() {
        FixedBackOff backOff = new FixedBackOff(retryBackoffMs, maxRetryAttempts);

        DefaultErrorHandler handler = new DefaultErrorHandler(
                (record, exception) -> log.error(
                        "Message processing failed after {} retries - topic={} partition={} offset={}: {}",
                        maxRetryAttempts,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        exception.getMessage()),
                backOff);

        return handler;
    }
}
