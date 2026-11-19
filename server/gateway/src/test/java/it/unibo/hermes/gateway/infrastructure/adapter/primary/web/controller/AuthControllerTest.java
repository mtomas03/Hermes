package it.unibo.hermes.gateway.infrastructure.adapter.primary.web.controller;

import it.unibo.hermes.gateway.application.model.AuthResult;
import it.unibo.hermes.gateway.application.port.primary.AuthPort;
import it.unibo.hermes.gateway.domain.exception.InvalidCredentialsException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthPort authServiceImpl;

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
        verify(authServiceImpl).register("alice", "password123");
    }

    @Test
    void registerShouldPropagateServiceException() {
        RegisterRequestDto request = new RegisterRequestDto("alice", "password123");
        doThrow(new IllegalArgumentException("Username 'alice' is already taken"))
                .when(authServiceImpl).register("alice", "password123");

        assertThatThrownBy(() -> controller.register(request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void loginShouldReturnAuthResponseOnSuccess() {
        LoginRequestDto request = new LoginRequestDto("alice", "secret");
        AuthResponseDto expected = new AuthResponseDto("jwt", "alice", 3_600_000L);
        when(authServiceImpl.login("alice", "secret"))
                .thenReturn(new AuthResult("jwt", "alice", 3_600_000L));

        ResponseEntity<AuthResponseDto> response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expected);
    }

    @Test
    void loginShouldPropagateInvalidCredentialsException() {
        LoginRequestDto request = new LoginRequestDto("alice", "wrong");
        when(authServiceImpl.login("alice", "wrong"))
                .thenThrow(new InvalidCredentialsException("Invalid credentials"));

        assertThatThrownBy(() -> controller.login(request))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
