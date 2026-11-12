package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.primary.AcknowledgementPort;
import it.unibo.hermes.gateway.application.port.secondary.AckPublisherPort;
import it.unibo.hermes.gateway.domain.exception.BackboneUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Service forwarding recipient acknowledgements to the message backbone, within a bounded time.
 */
@Service
public class AcknowledgementService implements AcknowledgementPort {

    private static final Logger log = LoggerFactory.getLogger(AcknowledgementService.class);

    private final AckPublisherPort ackPublisher;
    private final KafkaMonitorService backboneMonitor;

    @Value("${hermes.kafka.ack-budget-ms:2000}")
    private long ackBudgetMs;

    @Value("${hermes.kafka.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${hermes.kafka.retry.initial-backoff-ms:100}")
    private long initialBackoffMs;

    @Value("${hermes.kafka.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    /**
     * Creates the acknowledgement service.
     *
     * @param ackPublisher the port publishing acknowledgements to the backbone
     * @param backboneMonitor the monitor telling whether the backbone is reachable
     */
    public AcknowledgementService(AckPublisherPort ackPublisher, KafkaMonitorService backboneMonitor) {
        this.ackPublisher = ackPublisher;
        this.backboneMonitor = backboneMonitor;
    }

    @Override
    public void acknowledge(String messageId, String recipientUsername) {
        if (!backboneMonitor.isMessageBackboneAvailable()) {
            throw new BackboneUnavailableException(
                    "Backbone unavailable: acknowledgement of message " + messageId + " not recorded", null);
        }
        BoundedRetry.Outcome outcome = BoundedRetry.run(
                remaining -> ackPublisher.publishAck(messageId, recipientUsername, remaining),
                maxAttempts, initialBackoffMs, backoffMultiplier,
                Duration.ofMillis(ackBudgetMs), false);
        if (outcome.result() != BoundedRetry.Result.SUCCESS) {
            log.warn("Acknowledgement of message {} could not be published", messageId);
            backboneMonitor.reportBackboneFailure();
            throw new BackboneUnavailableException(
                    "Acknowledgement of message " + messageId + " could not be recorded", outcome.lastFailure());
        }
    }
}
