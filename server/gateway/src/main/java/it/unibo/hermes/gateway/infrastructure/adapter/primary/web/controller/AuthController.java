package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;


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
 * REST controller providing endpoints for user account registration and authentication.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthPort authPort;

    /**
     * Creates the authentication controller.
     *
     * @param authPort the inbound port processing registration and login workflows
     */
    public AuthController(AuthPort authPort) {
        this.authPort = authPort;
    }

    /**
     * Registers a new user account.
     *
     * @param request the registration request containing credentials and account information
     * @return a response entity with HTTP status 201 Created upon successful account creation
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequestDto request) {
        authPort.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Authenticates user credentials and returns a signed JWT.
     *
     * @param request the authentication request containing user credentials
     * @return a response entity containing the authentication response and token payload
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
        AuthResponseDto response = authPort.login(request);
        return ResponseEntity.ok(response);
    }
}