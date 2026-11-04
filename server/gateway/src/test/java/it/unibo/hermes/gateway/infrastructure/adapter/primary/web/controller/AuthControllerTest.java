package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.service.AuthService;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.AuthResponseDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.LoginRequestDto;
import it.unibo.hermes.gateway.infrastructure.adapter.primary.web.dto.RegisterRequestDto;
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
    private AuthService authServiceImpl;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authServiceImpl);
    }

    @Test
    void registerShouldDelegateToServiceAndReturn201() {
        RegisterRequestDto request = new RegisterRequestDto("alice", "password123");

        ResponseEntity<Void> response = controller.register(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        verify(authServiceImpl).register(request);
    }

    @Test
    void registerShouldPropagateServiceException() {
        RegisterRequestDto request = new RegisterRequestDto("alice", "password123");
        doThrow(new IllegalArgumentException("Username 'alice' is already taken"))
                .when(authServiceImpl).register(request);

        assertThatThrownBy(() -> controller.register(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginShouldReturnAuthResponseOnSuccess() {
        LoginRequestDto request = new LoginRequestDto("alice", "secret");
        AuthResponseDto expected = new AuthResponseDto("jwt", "alice", 3_600_000L);
        when(authServiceImpl.login(request)).thenReturn(expected);

        ResponseEntity<AuthResponseDto> response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void loginShouldPropagateBadCredentialsException() {
        LoginRequestDto request = new LoginRequestDto("alice", "wrong");
        when(authServiceImpl.login(request)).thenThrow(new BadCredentialsException("Invalid credentials"));

        assertThatThrownBy(() -> controller.login(request))
                .isInstanceOf(BadCredentialsException.class);
    }
}
