package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.model.AuthResult;
import it.unibo.hermes.gateway.application.port.primary.AuthPort;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.AuthResponseDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.LoginRequestDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.RegisterRequestDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller providing endpoints for user account registration and login.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthPort authPort;

    /**
     * Creates the authentication controller.
     *
     * @param authPort the primary port handling registration and login
     */
    public AuthController(AuthPort authPort) {
        this.authPort = authPort;
    }

    /**
     * Registers a new user.
     *
     * @param request the registration request
     * @return 201 Created on success
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequestDto request) {
        authPort.register(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Authenticates a user and returns a signed JWT.
     *
     * @param request the login request
     * @return 200 OK with the token
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResult result = authPort.login(request.username(), request.password());
        return ResponseEntity.ok(new AuthResponseDto(result.token(), result.username(), result.expiresInMs()));
    }
}
