package it.unibo.hermes.gateway.application.service;

import it.unibo.hermes.gateway.application.model.AuthResult;
import it.unibo.hermes.gateway.application.port.secondary.PasswordHasherPort;
import it.unibo.hermes.gateway.application.port.secondary.TokenIssuerPort;
import it.unibo.hermes.gateway.application.port.secondary.UserAccountPort;
import it.unibo.hermes.gateway.domain.exception.InvalidCredentialsException;
import it.unibo.hermes.gateway.domain.exception.UsernameAlreadyTakenException;
import it.unibo.hermes.gateway.domain.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountPort userAccounts;
    @Mock
    private PasswordHasherPort passwordHasher;
    @Mock
    private TokenIssuerPort tokenIssuer;

    private AuthService authServiceImpl;

    @BeforeEach
    void setUp() {
        authServiceImpl = new AuthService(userAccounts, passwordHasher, tokenIssuer);
    }

    @Test
    void shouldRegisterNewUserWithHashedPassword() {
        when(userAccounts.existsByUsername("alice")).thenReturn(false);
        when(passwordHasher.encode("secret123")).thenReturn("hashed-secret");

        authServiceImpl.register("alice", "secret123");

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccounts).save(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("alice");
        assertThat(captor.getValue().passwordHash()).isEqualTo("hashed-secret");
    }

    @Test
    void shouldRejectRegistrationOfDuplicateUsername() {
        when(userAccounts.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authServiceImpl.register("alice", "secret123"))
                .isInstanceOf(UsernameAlreadyTakenException.class)
                .isInstanceOf(IllegalArgumentException.class);
        verify(userAccounts, never()).save(any());
    }

    @Test
    void shouldLoginWithValidCredentials() {
        when(userAccounts.findByUsername("alice")).thenReturn(Optional.of(new UserAccount("alice", "hashed-secret")));
        when(passwordHasher.matches("secret123", "hashed-secret")).thenReturn(true);
        when(tokenIssuer.generateToken("alice")).thenReturn("jwt-token");
        when(tokenIssuer.getExpirationMs()).thenReturn(10_000L);

        AuthResult response = authServiceImpl.login("alice", "secret123");

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.expiresInMs()).isEqualTo(10_000L);
    }

    @Test
    void shouldRejectLoginForUnknownUser() {
        when(userAccounts.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authServiceImpl.login("ghost", "whatever"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenIssuer, never()).generateToken(any());
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        when(userAccounts.findByUsername("alice")).thenReturn(Optional.of(new UserAccount("alice", "hashed-secret")));
        when(passwordHasher.matches("wrong-password", "hashed-secret")).thenReturn(false);

        assertThatThrownBy(() -> authServiceImpl.login("alice", "wrong-password"))
                .isInstanceOf(InvalidCredentialsException.class);
        verify(tokenIssuer, never()).generateToken(any());
    }
}
