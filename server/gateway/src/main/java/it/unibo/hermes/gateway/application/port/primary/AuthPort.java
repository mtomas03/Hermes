package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.AuthResponseDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.LoginRequestDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.RegisterRequestDto;

/**
 * Port interface for authentication, including user registration and login.
 */
public interface AuthPort {

    /**
     * Registers a new user account with the provided registration details.
     *
     * @param request the registration request containing username and password
     * @throws IllegalArgumentException if the specified username is already registered
     */
    void register(RegisterRequestDto request);

    /**
     * Authenticates a user with the provided login credentials.
     *
     * @param request the login request containing username and password
     * @return an authentication response containing the JWT token if authentication is successful
     */
    AuthResponseDto login(LoginRequestDto request);
}
