package it.unibo.hermes.client.model.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Holds the JWT and associated metadata obtained after login.
 */
public record AuthToken(String rawToken, Instant expiresAt) {
    public AuthToken {
        Objects.requireNonNull(rawToken);
        Objects.requireNonNull(expiresAt);
    }

    /**
     * Returns true if the token has expired.
     */
    public boolean isExpired() {
        return !Instant.now().isBefore(expiresAt);
    }

    public String bearerHeader() {
        return "Bearer " + rawToken;
    }
}
