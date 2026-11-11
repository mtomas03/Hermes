package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.UserSearchPort;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing endpoints for user search.
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserSearchController {

    private final UserSearchPort userSearchPort;

    /**
     * Creates the user search controller.
     *
     * @param userSearchPort the primary port searching users
     */
    public UserSearchController(UserSearchPort userSearchPort) {
        this.userSearchPort = userSearchPort;
    }

    /**
     * Searches for a user by username.
     *
     * @param username the username to search for
     * @return the matching user
     */
    @GetMapping("/search")
    public ResponseEntity<UserDto> searchUser(@RequestParam("username") String username) {
        return ResponseEntity.ok(new UserDto(userSearchPort.searchUserByUsername(username).username()));
    }
}
