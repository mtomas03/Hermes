package it.unibo.hermes.gateway.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Represents an authenticated user persisted in PostgreSQL.
 */
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = "username"))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /**
     * The BCrypt-hashed representation of the user's password.
     */
    @Column(nullable = false)
    private String passwordHash;

    /**
     * The physical timestamp recording when this user account was created.
     */
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
    }

    /**
     * Creates a new authenticated user.
     *
     * @param username     the unique name of the user
     * @param passwordHash the BCrypt hash of the user's password
     */
    public User(String username, String passwordHash) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}