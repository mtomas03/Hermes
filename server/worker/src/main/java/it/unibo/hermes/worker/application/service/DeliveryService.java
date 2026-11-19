package it.unibo.hermes.worker.application.service;

import it.unibo.hermes.worker.application.port.primary.DeliveryPort;
import it.unibo.hermes.worker.application.port.secondary.MessageDeliveryProducerPort;
import it.unibo.hermes.worker.application.port.secondary.MessageStorePort;
import it.unibo.hermes.worker.application.port.secondary.PresenceLookupPort;
import it.unibo.hermes.worker.domain.model.DeliveryStatus;
import it.unibo.hermes.worker.domain.model.Message;
import it.unibo.hermes.worker.domain.model.PresenceInfo;
import it.unibo.hermes.worker.domain.model.StoredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service responsible for managing the message delivery pipeline: persist, then route to the recipient's gateway
 * if the recipient is online, otherwise leave the message stored for the next synchronisation of the recipient.
 */
@Service
public class DeliveryService implements DeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final MessageStorePort messageStore;
    private final PresenceLookupPort presence;
    private final MessageDeliveryProducerPort deliveryEvents;

    public DeliveryService(
            MessageStorePort messageStore,
            PresenceLookupPort presence,
            MessageDeliveryProducerPort deliveryEvents) {
        this.messageStore = messageStore;
        this.presence = presence;
        this.deliveryEvents = deliveryEvents;
    }

    @Override
    public void processMessage(Message message) {
        String messageId = message.messageId();

        Optional<StoredMessage> existing = messageStore.findById(messageId);
        if (existing.isPresent() && existing.get().isProcessed()) {
            log.info("Message {} already processed - skipping duplicate event", messageId);
            messageStore.reconcileProjection(messageId);
            return;
        }

        StoredMessage stored = existing.orElseGet(() -> messageStore
                .createIfAbsent(message));
        stored.message().verifyConsistentWith(message);
        messageStore.reconcileProjection(messageId);

        Optional<PresenceInfo> recipientPresence = presence
                .findPresence(message.recipientUsername());
        if (recipientPresence.isPresent() && recipientPresence.get().online()) {
            handleOnlineDelivery(message, recipientPresence.get());
        } else {
            handleOfflineStorage(message);
        }
    }

    private void handleOnlineDelivery(Message message, PresenceInfo recipientPresence) {
        try {
            deliveryEvents.publish(message, recipientPresence.gatewayId());
            messageStore.advanceStatus(message.messageId(), DeliveryStatus.DELIVERING);
            log.info("Message {} forwarded to gateway {} for recipient {}",
                    message.messageId(), recipientPresence.gatewayId(), message.recipientUsername());
        } catch (Exception e) {
            log.warn("Failed to forward message {} to gateway {} - falling back to STORED: {}",
                    message.messageId(), recipientPresence.gatewayId(), e.getMessage());
            messageStore.advanceStatus(message.messageId(), DeliveryStatus.STORED);
        }
    }

    private void handleOfflineStorage(Message message) {
        messageStore.advanceStatus(message.messageId(), DeliveryStatus.STORED);
        log.info("Recipient {} is offline - message {} stored for sync",
                message.recipientUsername(), message.messageId());
    }
}
