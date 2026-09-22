package it.unibo.hermes.client.dto;

/**
 * Data Transfer Object representing an error pushed by the server
 * to the sender's STOMP subscription at {@code /user/queue/errors}
 * in response to a rejected request.
 *
 * @param code          an error code identifying the type of error
 * @param reason        an explanation of the error
 * @param messageId     the identifier of the message the error refers to, if applicable
 */
public record ErrorDto(
        String code,
        String reason,
        String messageId
) {}
