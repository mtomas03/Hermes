package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.domain.User;
import it.unibo.hermes.gateway.dto.AuthResponse;
import it.unibo.hermes.gateway.dto.LoginRequest;
import it.unibo.hermes.gateway.dto.RegisterRequest;
import it.unibo.hermes.gateway.repository.UserRepository;
import it.unibo.hermes.gateway.security.JwtProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user account registration and authentication.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    /**
     * Creates the authentication service.
     *
     * @param userRepository  the repository managing user entity persistence
     * @param passwordEncoder the password encoder used to hash and verify credentials
     * @param jwtProvider     the provider generating signed JSON Web Tokens
     */
    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    /**
     * Registers a new user account with a securely hashed password.
     *
     * @param request the registration payload containing user credentials
     * @throws IllegalArgumentException if the specified username is already registered
     */
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException(
                    "Username '" + request.username() + "' is already taken");
        }
        String hash = passwordEncoder.encode(request.password());
        userRepository.save(new User(request.username(), hash));
        log.info("Registered new user '{}'", request.username());
    }

    /**
     * Validates user credentials and generates a signed authentication token upon success.
     *
     * @param request the authentication payload containing username and password credentials
     * @return an authentication response containing the signed token and token expiration duration
     * @throws BadCredentialsException if the user account does not exist or the password verification fails
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Invalid credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid credentials");
        }

        String token = jwtProvider.generateToken(user.getUsername());
        log.info("User '{}' authenticated", user.getUsername());
        return new AuthResponse(token, user.getUsername(), jwtProvider.getExpirationMs());
    }
}