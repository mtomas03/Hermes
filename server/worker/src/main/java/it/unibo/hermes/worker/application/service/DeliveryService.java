package it.unibo.hermes.worker.application.service;

import it.unibo.hermes.worker.application.port.primary.DeliveryPort;
import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.domain.PresenceInfo;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.CassandraAdapter;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.presence.RedisPresenceAdapter;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.producer.MessageDeliveryProducer;
import it.unibo.hermes.worker.infrastructure.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service responsible for orchestrating the message delivery pipeline.
 */
@Service
public class DeliveryService implements DeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final CassandraAdapter cassandraAdapter;
    private final RedisPresenceAdapter presenceAdapter;
    private final MessageDeliveryProducer messageDeliveryProducer;

    public DeliveryService(
            CassandraAdapter cassandraAdapter,
            RedisPresenceAdapter presenceAdapter,
            MessageDeliveryProducer messageDeliveryProducer) {
        this.cassandraAdapter = cassandraAdapter;
        this.presenceAdapter = presenceAdapter;
        this.messageDeliveryProducer = messageDeliveryProducer;
    }

    @Override
    public void processMessage(MessageEvent event) {
        String messageId = event.messageId();

        if (cassandraAdapter.isAlreadyProcessed(messageId)) {
            log.info("Message {} already processed - skipping duplicate event", messageId);
            cassandraAdapter.reconcileConversationProjection(messageId);
            return;
        }

        cassandraAdapter.persistMessage(event);

        Optional<PresenceInfo> presenceOpt = presenceAdapter.getPresence(event.recipientUsername());

        if (presenceOpt.isPresent() && presenceOpt.get().online()) {
            handleOnlineDelivery(event, presenceOpt.get());
        } else {
            handleOfflineStorage(event);
        }
    }

    private void handleOnlineDelivery(MessageEvent event, PresenceInfo presence) {
        try {
            messageDeliveryProducer.publishDeliveryEvent(event, presence.gatewayId());
            cassandraAdapter.updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.DELIVERING);
            log.info("Message {} forwarded to gateway {} for recipient {}",
                    event.messageId(), presence.gatewayId(), event.recipientUsername());
        } catch (Exception e) {
            log.warn("Failed to forward message {} to gateway {} - falling back to STORED: {}",
                    event.messageId(), presence.gatewayId(), e.getMessage());
            cassandraAdapter.updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        }
    }

    private void handleOfflineStorage(MessageEvent event) {
        cassandraAdapter.updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.STORED);
        log.info("Recipient {} is offline - message {} stored for sync",
                event.recipientUsername(), event.messageId());
    }
}
