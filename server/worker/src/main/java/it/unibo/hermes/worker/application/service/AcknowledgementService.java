package it.unibo.hermes.worker.application.service;

import it.unibo.hermes.worker.application.port.primary.AcknowledgementPort;
import it.unibo.hermes.worker.application.port.secondary.MessageStorePort;
import it.unibo.hermes.worker.domain.model.Acknowledgement;
import it.unibo.hermes.worker.domain.model.DeliveryStatus;
import it.unibo.hermes.worker.domain.model.StoredMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for processing delivery acknowledgements sent from gateway instances.
 */
@Service
public class AcknowledgementService implements AcknowledgementPort {

    private static final Logger log = LoggerFactory.getLogger(AcknowledgementService.class);

    private final MessageStorePort messageStore;

    public AcknowledgementService(MessageStorePort messageStore) {
        this.messageStore = messageStore;
    }

    @Override
    public void processAcknowledgement(Acknowledgement acknowledgement) {
        log.info("Processing ACK for message {} from recipient {}",
                acknowledgement.messageId(), acknowledgement.recipientUsername());

        Optional<StoredMessage> stored = messageStore.findById(acknowledgement.messageId());
        if (stored.isEmpty()) {
            log.warn("Ignoring ACK for unknown message {}", acknowledgement.messageId());
            return;
        }
        if (!stored.get().message().recipientUsername().equals(acknowledgement.recipientUsername())) {
            log.warn("Ignoring forged ACK for message {}: sender is not the recipient", acknowledgement.messageId());
            return;
        }

        messageStore.advanceStatus(acknowledgement.messageId(), DeliveryStatus.ACKNOWLEDGED);
        log.info("Message {} marked as ACKNOWLEDGED", acknowledgement.messageId());
    }
}
