package it.unibo.hermes.gateway.controller;

import it.unibo.hermes.gateway.dto.AuthResponse;
import it.unibo.hermes.gateway.dto.LoginRequest;
import it.unibo.hermes.gateway.dto.RegisterRequest;
import it.unibo.hermes.gateway.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    @Test
    void registerShouldDelegateToServiceAndReturn201() {
        RegisterRequest request = new RegisterRequest("alice", "password123");

        ResponseEntity<Void> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(authService).register(request);
    }

    @Test
    void registerShouldPropagateServiceException() {
        RegisterRequest request = new RegisterRequest("alice", "password123");
        doThrow(new IllegalArgumentException("Username 'alice' is already taken"))
                .when(authService).register(request);

        assertThatThrownBy(() -> controller.register(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginShouldReturnAuthResponseOnSuccess() {
        LoginRequest request = new LoginRequest("alice", "secret");
        AuthResponse expected = new AuthResponse("jwt", "alice", 3_600_000L);
        when(authService.login(request)).thenReturn(expected);

        ResponseEntity<AuthResponse> response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void loginShouldPropagateBadCredentialsException() {
        LoginRequest request = new LoginRequest("alice", "wrong");
        when(authService.login(request)).thenThrow(new BadCredentialsException("Invalid credentials"));

        assertThatThrownBy(() -> controller.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
