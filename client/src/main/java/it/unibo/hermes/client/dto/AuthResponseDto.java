package it.unibo.hermes.client.dto;

/**
 * Data transfer object for the login response.
 *
 * @param token          the authentication token
 * @param username       the username of the logged-in user
 * @param expiresInMs    the time in milliseconds after which the token expires
 */
public record AuthResponseDto(
        String token,
        String username,
        long expiresInMs
) {}
