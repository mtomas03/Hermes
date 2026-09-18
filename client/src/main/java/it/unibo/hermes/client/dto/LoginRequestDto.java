package it.unibo.hermes.client.dto;

/**
 * Data Transfer Object representing a login request.
 *
 * @param username      the unique identifier for the user attempting to log in
 * @param password      the password associated with the username for authentication
 */
public record LoginRequestDto(
        String username,
        String password
) {}
