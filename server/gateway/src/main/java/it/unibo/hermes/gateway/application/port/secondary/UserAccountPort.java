package it.unibo.hermes.gateway.application.port.secondary;

import it.unibo.hermes.gateway.domain.model.UserAccount;

import java.util.Optional;

/**
 * Secondary port interface for user account persistence and retrieval.
 */
public interface UserAccountPort {

    /**
     * Checks if a user account exists by username.
     *
     * @param username the username to check
     * @return true if the user account exists, false otherwise
     */
    boolean existsByUsername(String username);

    /**
     * Saves a user account.
     *
     * @param account the user account to save
     */
    void save(UserAccount account);

    /**
     * Finds a user account by username.
     *
     * @param username the username of the user account to find
     * @return an Optional containing the user account if found, or empty if not found
     */
    Optional<UserAccount> findByUsername(String username);
}
