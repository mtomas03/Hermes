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
        if (!(o instanceof User u)) return false;
        return username.equals(u.username);
    }

    @Override
    public int hashCode() {
        return username.hashCode();
    }
}
