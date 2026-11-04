package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.port.primary.UserSearchPort;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.UserDto;
import it.unibo.hermes.gateway.infrastructure.adapter.secondary.persistence.repository.jpa.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserSearchService implements UserSearchPort {

    private final UserRepository userRepository;

    public UserSearchService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Searches for a user by their username.
     *
     * @param username the username of the user to search for
     * @return a UserDto representing the found user
     * @throws ResponseStatusException if the user is not found
     */
    @Override
    public UserDto searchUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> new UserDto(user.getUsername()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found: " + username
                ));
    }
}
