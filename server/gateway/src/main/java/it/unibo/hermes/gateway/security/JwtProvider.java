package it.unibo.hermes.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
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
public class JwtProvider {

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
        byte[] keyBytes = Decoders.BASE64.decode(base64Secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generates a signed JWT containing the specified username as its subject.
     *
     * @param username the username to set as the token subject
     * @return the compact, signed JWT string
     */
    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
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
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Returns the token expiration duration in milliseconds.
     *
     * @return the expiration duration in milliseconds
     */
    public long getExpirationMs() {
        return expirationMs;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}