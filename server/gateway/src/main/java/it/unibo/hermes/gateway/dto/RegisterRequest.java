package it.unibo.hermes.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data transfer object representing the payload submitted during user registration.
 *
 * @param username the requested username, requiring a length between 3 and 64 characters
 * @param password the requested password, requiring a minimum length of 8 characters
 */
public record RegisterRequest(

        @NotBlank(message = "username is required")
        @Size(min = 3, max = 64, message = "username must be 3–64 characters")
        String username,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password
) {}
