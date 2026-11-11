package it.unibo.hermes.gateway.application.port.primary;

import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.UserDto;

/**
 * Port interface for searching users by their username.
 */
public interface UserSearchPort {

    /**
     * Searches for a user by their username.
     *
     * @param username the username of the user to search for
     * @return a UserDto representing the found user
     */
    UserDto searchUserByUsername(String username);
}
