package it.unibo.hermes.gateway.controller;


import it.unibo.hermes.gateway.dto.AuthResponse;
import it.unibo.hermes.gateway.dto.LoginRequest;
import it.unibo.hermes.gateway.dto.RegisterRequest;
import it.unibo.hermes.gateway.service.AuthService;
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

    private final AuthService authService;

    /**
     * Creates the authentication controller with the required authentication service.
     *
     * @param authService the service processing registration and login workflows
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registers a new user account.
     *
     * @param request the registration request containing credentials and account information
     * @return a response entity with HTTP status 201 Created upon successful account creation
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * Authenticates user credentials and returns a signed JWT.
     *
     * @param request the authentication request containing user credentials
     * @return a response entity containing the authentication response and token payload
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}