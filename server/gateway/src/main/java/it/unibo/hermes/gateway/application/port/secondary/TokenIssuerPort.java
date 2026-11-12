package it.unibo.hermes.gateway.application.port.secondary;

/**
 * Secondary port interface for issuing tokens for user authentication.
 */
public interface TokenIssuerPort {

    /**
     * Generates a token for a given username.
     *
     * @param username the username for which to generate the token
     * @return the generated token
     */
    String generateToken(String username);

    /**
     * Retrieves the expiration time of the generated tokens in milliseconds.
     *
     * @return the expiration time in milliseconds
     */
    long getExpirationMs();
}
