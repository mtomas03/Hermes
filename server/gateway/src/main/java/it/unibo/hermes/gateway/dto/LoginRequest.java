package it.unibo.hermes.gateway.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Data transfer object representing the payload submitted during user authentication.
 *
 * @param username the username submitted for authentication, which must not be blank
 * @param password the plaintext password submitted for authentication, which must not be blank
 */
public record LoginRequest(

        @NotBlank(message = "username is required")
        String username,

        @NotBlank(message = "password is required")
        String password
) {
}
