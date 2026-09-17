package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.dto.UserDto;
import it.unibo.hermes.gateway.repository.jpa.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Searches for a user by their username.
     *
     * @param username      the username of the user to search for
     * @return a UserDto representing the found user
     * @throws ResponseStatusException if the user is not found
     */
    public UserDto searchUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(user -> new UserDto(user.getUsername()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found: " + username
                ));
    }
}
