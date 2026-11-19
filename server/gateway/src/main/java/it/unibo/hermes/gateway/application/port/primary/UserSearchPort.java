package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.domain.exception.UserNotFoundException;
import it.unibo.hermes.gateway.domain.model.UserProfile;

/**
 * Primary port interface for searching users by their username.
 */
public interface UserSearchPort {

    /**
     * Searches for a user by username.
     *
     * @param username the username to look up
     * @return the matching user profile
     * @throws UserNotFoundException if no such user exists
     */
    UserProfile searchUserByUsername(String username);
}
