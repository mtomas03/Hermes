package it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence;

import it.unibo.hermes.gateway.application.port.secondary.UserAccountPort;
import it.unibo.hermes.gateway.domain.model.UserAccount;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.entity.jpa.User;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.repository.jpa.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Secondary adapter implementing {@link UserAccountPort} on top of PostgreSQL/JPA
 * by providing persistence and retrieval methods for user accounts.
 */
@Component
public class JpaUserAccountAdapter implements UserAccountPort {

    private final UserRepository userRepository;

    public JpaUserAccountAdapter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Override
    public void save(UserAccount account) {
        userRepository.save(new User(account.username(), account.passwordHash()));
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> new UserAccount(user.getUsername(), user.getPasswordHash()));
    }
}
