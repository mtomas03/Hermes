package it.unibo.hermes.gateway.dto;

/**
 * Data transfer object representing the response payload returned after a successful authentication request.
 *
 * @param token        the JSON token used for authenticating API requests
 * @param username     the unique name of the authenticated user
 * @param expiresInMs  the validity duration of the issued token expressed in milliseconds
 */
public record AuthResponse(
        String token,
        String username,
        long expiresInMs
) {}
