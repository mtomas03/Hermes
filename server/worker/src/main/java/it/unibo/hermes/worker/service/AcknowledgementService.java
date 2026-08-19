package it.unibo.hermes.worker.service;

import it.unibo.hermes.worker.domain.DeliveryStatus;
import it.unibo.hermes.worker.event.MessageAcknowledgedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service responsible for processing delivery acknowledgements sent by the Gateway
 * when a recipient's WebSocket session explicitly confirms receipt of a message.
 *
 * <p> Receiving a {@link MessageAcknowledgedEvent} serves as confirmation that a message
 * has successfully reached its recipient. Upon receiving this event, the service updates
 * the message's state to {@link DeliveryStatus#ACKNOWLEDGED}.
 *
 * <p> If the message has already reached the {@code ACKNOWLEDGED}
 * state, the {@link PersistenceService} prevents redundant or
 * invalid state modifications.
 */
@Service
public class AcknowledgementService {

    private static final Logger log = LoggerFactory.getLogger(AcknowledgementService.class);

    private final PersistenceService persistenceService;

    /**
     * Creates a new {@code AcknowledgementService}.
     *
     * @param persistenceService the persistence service used to update message statuses
     */
    public AcknowledgementService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * Processes a message delivery confirmation and transitions the message status to
     * {@link DeliveryStatus#ACKNOWLEDGED}.
     *
     * @param event the acknowledgement event containing message and recipient details
     */
    public void processAcknowledgement(MessageAcknowledgedEvent event) {
        log.info("Processing acknowledgement for message {} from recipient {}",
                event.messageId(), event.recipientUsername());

        persistenceService.updateDeliveryStatus(event.messageId(), DeliveryStatus.ACKNOWLEDGED);

        log.info("Message {} marked as ACKNOWLEDGED", event.messageId());
    }
}