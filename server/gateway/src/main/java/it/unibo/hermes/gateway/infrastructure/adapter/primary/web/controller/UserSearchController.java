package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.port.primary.UserSearchPort;
import it.unibo.hermes.gateway.application.service.UserSearchService;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserSearchController {

    private final UserSearchPort userSearchPort;

    /**
     * Constructs the user search controller.
     *
     * @param userSearchPort the inbound port that is responsible for searching users
     */
    public UserSearchController(UserSearchService userSearchPort) {
        this.userSearchPort = userSearchPort;
    }

    /**
     * Searches for a user by their username.
     *
     * @param username the username of the user to search for
     * @return a ResponseEntity containing the UserDto if found, or an appropriate error response if not found
     */
    @GetMapping("/search")
    public ResponseEntity<UserDto> searchUser(@RequestParam("username") String username) {
        UserDto user = userSearchPort.searchUserByUsername(username);
        return ResponseEntity.ok(user);
    }
}
