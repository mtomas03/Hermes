package it.unibo.hermes.client.dto;

/**
 * Data Transfer Object representing the ACK sent by a client over STOMP to {@code /app/chat.ack},
 * confirming that it has received a message pushed to {@code /user/queue/messages}
 * and persisted it locally.
 *
 * <p>The Gateway derives the acknowledging user from the authenticated STOMP {@code Principal}.
 *
 * @param messageId the unique identifier of the acknowledged message
 */
public record DeliveryAckDto(
        String messageId
) {}
