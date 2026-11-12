package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.primary.UserSearchPort;
import it.unibo.hermes.gateway.application.port.secondary.UserAccountPort;
import it.unibo.hermes.gateway.domain.exception.UserNotFoundException;
import it.unibo.hermes.gateway.domain.model.UserProfile;
import org.springframework.stereotype.Service;

/**
 * Service searching registered users by username.
 */
@Service
public class UserSearchService implements UserSearchPort {

    private final UserAccountPort userAccounts;

    /**
     * Creates the user search service.
     *
     * @param userAccounts the port providing user accounts
     */
    public UserSearchService(UserAccountPort userAccounts) {
        this.userAccounts = userAccounts;
    }

    @Override
    public UserProfile searchUserByUsername(String username) {
        return userAccounts.findByUsername(username)
                .map(account -> new UserProfile(account.username()))
                .orElseThrow(() -> new UserNotFoundException("User not found: " + username));
    }
}
