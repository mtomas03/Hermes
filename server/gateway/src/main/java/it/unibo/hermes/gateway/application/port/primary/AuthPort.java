package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.application.model.AuthResult;
import it.unibo.hermes.gateway.domain.exception.InvalidCredentialsException;
import it.unibo.hermes.gateway.domain.exception.UsernameAlreadyTakenException;

/**
 * Primary port interface for authentication, including user registration and login.
 */
public interface AuthPort {

    /**
     * Registers a new user account.
     *
     * @param username    the requested username
     * @param rawPassword the clear-text password
     * @throws UsernameAlreadyTakenException if the username is taken
     */
    void register(String username, String rawPassword);

    /**
     * Authenticates a user.
     *
     * @param username    the username
     * @param rawPassword the clear-text password
     * @return the authentication result containing the access token
     * @throws InvalidCredentialsException if the credentials are wrong
     */
    AuthResult login(String username, String rawPassword);
}
