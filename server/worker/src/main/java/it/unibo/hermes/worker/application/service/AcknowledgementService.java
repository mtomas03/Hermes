package it.unibo.hermes.worker.application.service;

import it.unibo.hermes.worker.application.port.primary.AcknowledgementPort;
import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.infrastructure.adapter.secondary.persistence.CassandraAdapter;
import it.unibo.hermes.worker.infrastructure.event.MessageAckEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service responsible for processing delivery acknowledgements from Gateway instances.
 */
@Service
public class AcknowledgementService implements AcknowledgementPort {

    private static final Logger log = LoggerFactory.getLogger(AcknowledgementService.class);

    private final CassandraAdapter cassandraAdapter;

    public AcknowledgementService(CassandraAdapter cassandraAdapter) {
        this.cassandraAdapter = cassandraAdapter;
    }

    @Override
    public void processAcknowledgement(MessageAckEvent event) {
        log.info("Processing ACK for message {} from recipient {}", event.messageId(), event.recipientUsername());

        Optional<String> actualRecipient = cassandraAdapter.findRecipientUsername(event.messageId());
        if (actualRecipient.isEmpty()) {
            log.warn("Ignoring ACK for unknown message {}", event.messageId());
            return;
        }
        if (!actualRecipient.get().equals(event.recipientUsername())) {
            log.warn("Ignoring forged ACK for message {}: sender is not the recipient", event.messageId());
            return;
        }

        cassandraAdapter.updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.ACKNOWLEDGED);
        log.info("Message {} marked as ACKNOWLEDGED in Cassandra", event.messageId());
    }
}
