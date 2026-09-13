package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.adapter.CassandraMessageAdapter;
import it.unibo.hermes.worker.adapter.RedisPresenceAdapter;
import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.domain.PresenceInfo;
import it.unibo.hermes.worker.event.MessageEvent;
import it.unibo.hermes.worker.producer.MessageDeliveryProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service responsible for orchestrating the message delivery pipeline.
 */
@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final CassandraMessageAdapter cassandraAdapter;
    private final RedisPresenceAdapter presenceAdapter;
    private final MessageDeliveryProducer messageDeliveryProducer;

    public DeliveryService(
            CassandraMessageAdapter cassandraAdapter,
            RedisPresenceAdapter presenceAdapter,
            MessageDeliveryProducer messageDeliveryProducer) {
        this.cassandraAdapter = cassandraAdapter;
        this.presenceAdapter = presenceAdapter;
        this.messageDeliveryProducer = messageDeliveryProducer;
    }

    public void processMessage(MessageEvent event) {
        String messageId = event.messageId();

        if (cassandraAdapter.isAlreadyProcessed(messageId)) {
            log.info("Message {} already processed - skipping duplicate event", messageId);
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
