package it.unibo.hermes.gateway.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = Base64.getEncoder()
            .encodeToString("01234567890123456789012345678901".getBytes());

    private JwtProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtProvider();
        ReflectionTestUtils.setField(provider, "base64Secret", SECRET);
        ReflectionTestUtils.setField(provider, "expirationMs", 60_000L);
        provider.init();
    }

    @Test
    void generatedTokenShouldBeValid() {
        String token = provider.generateToken("alice");

        assertThat(provider.isValid(token)).isTrue();
    }

    @Test
    void shouldExtractUsernameFromGeneratedToken() {
        String token = provider.generateToken("alice");

        assertThat(provider.extractUsername(token))
                .isEqualTo("alice");
    }

    @Test
    void malformedTokenShouldBeInvalid() {
        assertThat(provider.isValid("this.is.not-a-jwt")).isFalse();
    }

    @Test
    void tokenSignedWithDifferentKeyShouldBeInvalid() {
        // A token signed with a completely different secret
        String foreignToken = Jwts.builder()
                .subject("alice")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("99999999999999999999999999999999".getBytes()))
                .compact();

        assertThat(provider.isValid(foreignToken)).isFalse();
    }

    @Test
    void expiredTokenShouldBeInvalid() {
        ReflectionTestUtils.setField(provider, "expirationMs", -1000L);
        String expired = provider.generateToken("alice");

        assertThat(provider.isValid(expired)).isFalse();
    }

    @Test
    void shouldExposeConfiguredExpirationDuration() {
        assertThat(provider.getExpirationMs()).isEqualTo(60_000L);
    }
}
