package it.unibo.hermes.gateway.controller;

import it.unibo.hermes.gateway.dto.UserDto;
import it.unibo.hermes.gateway.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/search")
    public ResponseEntity<UserDto> searchUser(@RequestParam("username") String username) {
        UserDto user = userService.searchUserByUsername(username);
        return ResponseEntity.ok(user);
    }
}
