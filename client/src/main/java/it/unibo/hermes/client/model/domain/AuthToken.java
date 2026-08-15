package it.unibo.hermes.client.model.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Holds the JWT and associated metadata obtained after login.
 */
public record AuthToken(String rawToken, Instant expiresAt) {
    public AuthToken(String rawToken, Instant expiresAt) {
        this.rawToken = Objects.requireNonNull(rawToken);
        this.expiresAt = expiresAt;
    }

    /**
     * Returns true if the token has not yet expired.
     */
    public boolean isValid() {
        return expiresAt != null && Instant.now().isBefore(expiresAt);
    }

    public String bearerHeader() {
        return "Bearer " + rawToken;
    }
}
