package it.unibo.hermes.worker.infrastructure.adapter.primary.mapper;

import it.unibo.hermes.worker.domain.exception.InvalidMessageException;
import it.unibo.hermes.worker.domain.model.Message;
import it.unibo.hermes.worker.infrastructure.event.MessageEvent;

/**
 * Maps the {@code message-created} DTO to the Domain {@link Message}.
 */
public final class MessageEventMapper {

    private MessageEventMapper() {
    }

    /**
     * Maps a {@link MessageEvent} to a Domain {@link Message}.
     *
     * @param event the deserialized DTO
     * @return the equivalent Domain message
     * @throws InvalidMessageException if the event does not describe a valid message
     */
    public static Message toDomain(MessageEvent event) {
        if (event == null) {
            throw new InvalidMessageException("The message event is empty");
        }
        return new Message(
                event.messageId(),
                event.conversationId(),
                event.senderUsername(),
                event.recipientUsername(),
                event.content(),
                event.logicalTimestamp());
    }
}
