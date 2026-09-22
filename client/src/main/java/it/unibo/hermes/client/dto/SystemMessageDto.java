package it.unibo.hermes.client.dto;

/**
 * Data Transfer Object representing a server-initiated control message pushed
 * to a user's STOMP subscription at {@code /user/queue/system}.
 *
 * @param type      the control message type
 * @param reason    the reason for the control message
 */
public record SystemMessageDto(
        String type,
        String reason
) {}
