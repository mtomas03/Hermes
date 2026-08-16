package it.unibo.hermes.gateway.repository;

import it.unibo.hermes.gateway.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository managing persistent operations and account lookups for {@link User} entities.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Finds a user entity by its unique username.
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the user if found, or empty if no matching record exists
     */
    Optional<User> findByUsername(String username);

    /**
     * Checks whether an account exists with the specified username.
     *
     * @param username the username to verify
     * @return {@code true} if a user exists with the given username, {@code false} otherwise
     */
    boolean existsByUsername(String username);
}