package it.unibo.hermes.gateway.domain.model;

/**
 * A registered user account.
 *
 * @param username     the unique username
 * @param passwordHash the hashed password (never the clear text)
 */
public record UserAccount(String username, String passwordHash) {
}
