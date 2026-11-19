package it.unibo.hermes.worker.infrastructure.adapter.primary.mapper;

import it.unibo.hermes.worker.domain.exception.InvalidAcknowledgementException;
import it.unibo.hermes.worker.domain.model.Acknowledgement;
import it.unibo.hermes.worker.infrastructure.event.MessageAckEvent;

/**
 * Maps the {@code message-acknowledged} DTO to the Domain {@link Acknowledgement}.
 */
public final class AcknowledgementEventMapper {

    private AcknowledgementEventMapper() {
    }

    /**
     * Maps a {@link MessageAckEvent} to a Domain {@link Acknowledgement}.
     *
     * @param event the deserialized DTO
     * @return the equivalent Domain acknowledgement
     * @throws InvalidAcknowledgementException if the event does not describe a valid acknowledgement
     */
    public static Acknowledgement toDomain(MessageAckEvent event) {
        if (event == null) {
            throw new InvalidAcknowledgementException("The acknowledgement event is empty");
        }
        return new Acknowledgement(event.messageId(), event.recipientUsername());
    }
}
