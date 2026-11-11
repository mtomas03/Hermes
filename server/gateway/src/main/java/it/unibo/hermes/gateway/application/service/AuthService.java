package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.model.AuthResult;
import it.unibo.hermes.gateway.application.port.primary.AuthPort;
import it.unibo.hermes.gateway.application.port.secondary.PasswordHasherPort;
import it.unibo.hermes.gateway.application.port.secondary.TokenIssuerPort;
import it.unibo.hermes.gateway.application.port.secondary.UserAccountPort;
import it.unibo.hermes.gateway.domain.exception.InvalidCredentialsException;
import it.unibo.hermes.gateway.domain.exception.UsernameAlreadyTakenException;
import it.unibo.hermes.gateway.domain.model.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user account registration and login.
 */
@Service
public class AuthService implements AuthPort {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserAccountPort userAccounts;
    private final PasswordHasherPort passwordHasher;
    private final TokenIssuerPort tokenIssuer;

    /**
     * Creates the authentication service.
     *
     * @param userAccounts   the port persisting user accounts
     * @param passwordHasher the port hashing and verifying passwords
     * @param tokenIssuer    the port issuing access tokens
     */
    public AuthService(UserAccountPort userAccounts,
                       PasswordHasherPort passwordHasher,
                       TokenIssuerPort tokenIssuer) {
        this.userAccounts = userAccounts;
        this.passwordHasher = passwordHasher;
        this.tokenIssuer = tokenIssuer;
    }

    @Transactional
    @Override
    public void register(String username, String rawPassword) {
        if (userAccounts.existsByUsername(username)) {
            throw new UsernameAlreadyTakenException("Username '" + username + "' is already taken");
        }
        String hash = passwordHasher.encode(rawPassword);
        userAccounts.save(new UserAccount(username, hash));
        log.info("Registered new user '{}'", username);
    }

    @Transactional(readOnly = true)
    @Override
    public AuthResult login(String username, String rawPassword) {
        UserAccount account = userAccounts.findByUsername(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));

        if (!passwordHasher.matches(rawPassword, account.passwordHash())) {
            throw new InvalidCredentialsException("Invalid credentials");
        }

        String token = tokenIssuer.generateToken(account.username());
        log.info("User '{}' authenticated", account.username());
        return new AuthResult(token, account.username(), tokenIssuer.getExpirationMs());
    }
}
