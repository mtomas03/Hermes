package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto;

/**
 * Data transfer object representing a user.
 *
 * @param username the unique username of the user
 */
public record UserDto(
        String username
) {
}
