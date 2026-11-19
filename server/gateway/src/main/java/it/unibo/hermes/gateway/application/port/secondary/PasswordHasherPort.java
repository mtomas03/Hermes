package it.unibo.hermes.gateway.application.port.secondary;

/**
 * Secondary port interface for password hashing.
 */
public interface PasswordHasherPort {

    /**
     * Encodes a raw password.
     *
     * @param rawPassword the raw password to encode
     * @return the encoded password
     */
    String encode(String rawPassword);

    /**
     * Checks if a raw password matches an encoded password.
     *
     * @param rawPassword     the raw password to check
     * @param encodedPassword the encoded password to check against
     * @return true if the raw password matches the encoded password, false otherwise
     */
    boolean matches(String rawPassword, String encodedPassword);
}
