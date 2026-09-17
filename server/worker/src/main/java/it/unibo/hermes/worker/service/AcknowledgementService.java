package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.adapter.CassandraAdapter;
import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.event.MessageAckEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for processing delivery acknowledgements from Gateway instances.
 */
@Service
public class AcknowledgementService {

    private static final Logger log = LoggerFactory.getLogger(AcknowledgementService.class);

    private final CassandraAdapter cassandraAdapter;

    public AcknowledgementService(CassandraAdapter cassandraAdapter) {
        this.cassandraAdapter = cassandraAdapter;
    }

    public void processAcknowledgement(MessageAckEvent event) {
        log.info("Processing ACK for message {} from recipient {}", event.messageId(), event.recipientUsername());
        cassandraAdapter.updateMessageDeliveryStatus(event.messageId(), DeliveryStatus.ACKNOWLEDGED);
        log.info("Message {} marked as ACKNOWLEDGED in Cassandra", event.messageId());
    }
}
