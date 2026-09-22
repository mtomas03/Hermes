package it.unibo.hermes.gateway.dto;

/**
 * Data Transfer Object representing an error pushed to the sender's STOMP subscription at
 * {@code /user/queue/errors}: sending this on a dedicated user destination does not terminate
 * the session, allowing the client to recover from errors without reconnecting.
 *
 * @param code      an error code identifying the type of error
 * @param reason    an explanation of the error
 * @param messageId the identifier of the message the error refers to, if applicable
 */
public record ErrorDto(
        String code,
        String reason,
        String messageId
) {}
