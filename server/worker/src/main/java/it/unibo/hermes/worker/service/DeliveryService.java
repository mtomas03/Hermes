package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.domain.PresenceInfo;
import it.unibo.hermes.worker.event.MessageCreatedEvent;
import it.unibo.hermes.worker.event.MessageDeliveryEvent;
import it.unibo.hermes.worker.producer.DeliveryEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service responsible for managing the delivery pipeline of newly created messages.
 *
 * <p> This implementation guarantees idempotent processing by ignoring duplicate events, ensures durable
 * persistence in storage before any delivery attempt to prevent data loss, conditionally routes messages
 * based on real-time recipient presence, and never reports delivery as successful unless persistence is confirmed.
 *
 * <p> If the recipient is online, a {@link MessageDeliveryEvent} is forwarded to the target gateway via Kafka
 * and the message status is updated to {@link DeliveryStatus#DELIVERING}. If the recipient is offline or if
 * Redis is unavailable, the message status is set to {@link DeliveryStatus#STORED} for retrieval during the
 * client's next synchronisation pull.
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final PersistenceService persistenceService;
    private final RedisPresenceService presenceService;
    private final DeliveryEventProducer deliveryEventProducer;

    /**
     * Creates a new {@code DeliveryService}.
     *
     * @param persistenceService    the persistence service for storing messages and updating statuses
     * @param presenceService       the Redis presence service for checking recipient availability
     * @param deliveryEventProducer the producer for publishing events to delivery Kafka topics
     */
    public DeliveryService(
            PersistenceService persistenceService,
            RedisPresenceService presenceService,
            DeliveryEventProducer deliveryEventProducer) {
        this.persistenceService = persistenceService;
        this.presenceService = presenceService;
        this.deliveryEventProducer = deliveryEventProducer;
    }

    /**
     * Processes a {@link MessageCreatedEvent}.
     *
     * @param event the creation event to process
     */
    public void processMessage(MessageCreatedEvent event) {
        String messageId = event.messageId();

        if (persistenceService.isAlreadyProcessed(messageId)) {
            log.info("Message {} already processed - skipping duplicate event", messageId);
            return;
        }

        // Persist the message before making any delivery decision.
        persistenceService.persistMessage(event);

        // Decide delivery path based on recipient presence.
        Optional<PresenceInfo> presenceOpt = presenceService.getPresence(event.recipientUsername());

        if (presenceOpt.isPresent() && presenceOpt.get().online()) {
            handleOnlineDelivery(event, presenceOpt.get());
        } else {
            handleOfflineStorage(event);
        }
    }

    /**
     * Publishes a delivery event to the target gateway and advances the status.
     * Falls back to STORED if the publishing fails so the Gateway can sync later.
     *
     * @param event    the message creation event being processed
     * @param presence the presence and routing information for the online recipient
     */
    private void handleOnlineDelivery(MessageCreatedEvent event, PresenceInfo presence) {
        try {
            deliveryEventProducer.publishDeliveryEvent(event, presence.gatewayId());
            persistenceService.updateDeliveryStatus(event.messageId(), DeliveryStatus.DELIVERING);
            log.info("Message {} forwarded to gateway {} for online recipient {}",
                    event.messageId(), presence.gatewayId(), event.recipientUsername());
        } catch (Exception e) {
            // The delivery event publish failed. The message is safely in Cassandra
            // with PENDING status; marking it STORED lets the Gateway sync it later.
            log.warn("Failed to forward message {} to gateway {} - falling back to STORED: {}",
                    event.messageId(), presence.gatewayId(), e.getMessage());
            persistenceService.updateDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        }
    }

    /**
     * Marks the message as STORED so the Gateway retrieves it during the next
     * client synchronisation pull.
     *
     * @param event the message creation event being processed
     */
    private void handleOfflineStorage(MessageCreatedEvent event) {
        persistenceService.updateDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        log.info("Recipient {} is offline - message {} stored for sync",
                event.recipientUsername(), event.messageId());
    }
}