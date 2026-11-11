package it.unibo.hermes.client.model.domain;

import java.util.Objects;

/**
 * Represents a user in the system.
 */
public record User(String username) {
    public User(String username) {
        this.username = Objects.requireNonNull(username);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User(String username1))) return false;
        return username.equals(username1);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }
}
