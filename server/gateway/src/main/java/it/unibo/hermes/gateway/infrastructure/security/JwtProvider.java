package it.unibo.hermes.gateway.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import it.unibo.hermes.gateway.application.port.secondary.TokenIssuerPort;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Utility component handling cryptographic signing, generation and verification of JWT.
 */
@Component
public class JwtProvider implements TokenIssuerPort {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);

    @Value("${hermes.jwt.secret}")
    private String base64Secret;

    @Value("${hermes.jwt.expiration-ms}")
    private long expirationMs;

    private SecretKey key;

    /**
     * Initialises the HMAC secret key, after dependency injection completes.
     */
    @PostConstruct
    void init() {
        byte[] keyBytes = Decoders.BASE64.decode(this.base64Secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    @Override
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + this.expirationMs);
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(this.key)
                .compact();
    }

    @Override
    public long getExpirationMs() {
        return this.expirationMs;
    }

    /**
     * Extracts the username from a valid token.
     *
     * @param token the JWT string
     * @return the username stored in the token
     * @throws JwtException if the token signature is invalid, expired, or malformed
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Verifies that the token is correctly formatted, signed with the matching key, and not expired.
     *
     * @param token the JWT string to validate
     * @return {@code true} if the token is valid, {@code false} otherwise
     */
    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT, reason: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(this.key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}