package it.unibo.hermes.client.dto;

/**
 * Data Transfer Object representing an acknowledgement message.
 *
 * @param messageId the unique identifier of the acknowledged message
 * @param status    the status of the message acknowledgement
 */
public record AckDto(
        String messageId,
        String status
) {}
