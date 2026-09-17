package it.unibo.hermes.gateway.dto;

/**
 * Data transfer object representing the response returned
 * after a successful authentication request.
 *
 * @param token       the JSON token used for authenticating API requests
 * @param username    the unique name of the authenticated user
 * @param expiresInMs the duration in milliseconds until the token expires
 */
public record AuthResponseDto(
        String token,
        String username,
        long expiresInMs
) {}
