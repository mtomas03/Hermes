package it.unibo.hermes.gateway.application.model;

/**
 * Result of a successful login.
 *
 * @param token       the authentication token
 * @param username    the authenticated username
 * @param expiresInMs the token lifetime in milliseconds
 */
public record AuthResult(String token, String username, long expiresInMs) {
}
