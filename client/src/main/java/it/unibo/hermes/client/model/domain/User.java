package it.unibo.hermes.client.model.domain;

import java.util.Objects;

/**
 * Represents a user in the system.
 */
public record User(String username) {
    public User {
        Objects.requireNonNull(username);
    }
}
