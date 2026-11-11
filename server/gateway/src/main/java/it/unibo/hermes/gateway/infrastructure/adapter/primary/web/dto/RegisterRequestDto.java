package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data transfer object representing the registration request
 * submitted during user registration.
 *
 * @param username the unique username for the new account
 * @param password the password for the new account
 */
public record RegisterRequestDto(

        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        String password
) {
}
