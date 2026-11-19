package it.unibo.hermes.gateway.infrastructure.adapter.secondary.security;

import it.unibo.hermes.gateway.application.port.secondary.PasswordHasherPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Secondary adapter implementing {@link PasswordHasherPort}
 * with the configured Spring {@link PasswordEncoder}.
 */
@Component
public class SpringPasswordHasher implements PasswordHasherPort {

    private final PasswordEncoder passwordEncoder;

    public SpringPasswordHasher(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String encode(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
