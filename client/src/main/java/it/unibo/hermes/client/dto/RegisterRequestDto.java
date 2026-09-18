package it.unibo.hermes.client.dto;

/**
 * Data Transfer Object representing a registration request.
 *
 * @param username      the unique identifier for the user attempting to register
 * @param password      the password associated with the username for authentication
 */
public record RegisterRequestDto(
        String username,
        String password
) {}
