package it.unibo.hermes.gateway.service;

import it.unibo.hermes.gateway.domain.User;
import it.unibo.hermes.gateway.dto.AuthResponse;
import it.unibo.hermes.gateway.dto.LoginRequest;
import it.unibo.hermes.gateway.dto.RegisterRequest;
import it.unibo.hermes.gateway.repository.UserRepository;
import it.unibo.hermes.gateway.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtProvider jwtProvider;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder, jwtProvider);
    }

    @Test
    void shouldRegisterNewUserWithHashedPassword() {
        when(userRepository.existsByUsername("alice")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed-secret");

        authService.register(new RegisterRequest("alice", "secret123"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("alice");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("hashed-secret");
    }

    @Test
    void shouldRejectRegistrationOfDuplicateUsername() {
        when(userRepository.existsByUsername("alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice", "secret123")))
                    .isInstanceOf(IllegalArgumentException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldLoginWithValidCredentials() {
        User stored = new User("alice", "hashed-secret");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("secret123", "hashed-secret")).thenReturn(true);
        when(jwtProvider.generateToken("alice")).thenReturn("jwt-token");
        when(jwtProvider.getExpirationMs()).thenReturn(10_000L);

        AuthResponse response = authService.login(new LoginRequest("alice", "secret123"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.expiresInMs()).isEqualTo(10_000L);
    }

    @Test
    void shouldRejectLoginForUnknownUser() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "whatever")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtProvider, never()).generateToken(any());
    }

    @Test
    void shouldRejectLoginWithWrongPassword() {
        User stored = new User("alice", "hashed-secret");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("wrong-password", "hashed-secret")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "wrong-password")))
                .isInstanceOf(BadCredentialsException.class);
        verify(jwtProvider, never()).generateToken(any());
    }
}
