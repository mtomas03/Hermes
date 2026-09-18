package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.dto.AuthResponseDto;
import it.unibo.hermes.client.model.domain.AuthToken;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.state.AuthState;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.service.AuthService;
import it.unibo.hermes.client.service.LocalPersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private static final long ASYNC_TIMEOUT_MS = 2000;

    @Mock
    private AuthService authService;
    @Mock
    private ClientStateModel stateModel;
    @Mock
    private LocalPersistenceService persistence;
    @Mock
    private ConnectionController connectionController;
    @Mock
    private SyncController syncController;
    @Mock
    private Consumer<String> onError;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService, stateModel, persistence, connectionController, syncController);
    }

    @Test
    void successfulLoginShouldStoreTokenAndUserAndAuthenticate() {
        AuthResponseDto response = new AuthResponseDto("jwt-token", "alice", 3_600_000);
        when(authService.login("alice", "secret")).thenReturn(Mono.just(response));

        controller.login("alice", "secret", onError);

        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setAuthToken(any(AuthToken.class));
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setCurrentUser(new User("alice"));
        verify(persistence, timeout(ASYNC_TIMEOUT_MS)).saveLocalUser(new User("alice"));
        verify(connectionController, timeout(ASYNC_TIMEOUT_MS)).connect("jwt-token");
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setAuthState(AuthState.AUTHENTICATED);
        verifyNoInteractions(onError);
    }

    @Test
    void loginShouldMoveToAuthenticatingStateImmediately() {
        when(authService.login(anyString(), anyString())).thenReturn(Mono.never());

        controller.login("alice", "secret", onError);

        // Happens synchronously, before the (never-completing) network call
        verify(stateModel).setAuthState(AuthState.AUTHENTICATING);
    }

    @Test
    void failedLoginWithInvalidCredentialsShouldReportFriendlyMessage() {
        when(authService.login("alice", "wrong"))
                .thenReturn(Mono.error(new RuntimeException("Invalid credentials")));

        controller.login("alice", "wrong", onError);

        verify(onError, timeout(ASYNC_TIMEOUT_MS)).accept("Invalid username or password.");
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setAuthState(AuthState.UNAUTHENTICATED);
    }

    @Test
    void loginFailureDueToConnectivityShouldReportServerUnreachable() {
        when(authService.login("alice", "secret"))
                .thenReturn(Mono.error(new RuntimeException("Connection refused")));

        controller.login("alice", "secret", onError);

        verify(onError, timeout(ASYNC_TIMEOUT_MS)).accept("Cannot reach the server. Check your connection.");
    }

    @Test
    void successfulRegistrationShouldInvokeSuccessCallback() {
        when(authService.register("newuser", "password123")).thenReturn(Mono.empty());
        Runnable onSuccess = mock(Runnable.class);

        controller.register("newuser", "password123", onSuccess, onError);

        verify(onSuccess, timeout(ASYNC_TIMEOUT_MS)).run();
        verifyNoInteractions(onError);
    }

    @Test
    void failedRegistrationShouldInvokeErrorCallback() {
        when(authService.register("taken", "password123"))
                .thenReturn(Mono.error(new RuntimeException("Registration failed: username taken")));
        Runnable onSuccess = mock(Runnable.class);

        controller.register("taken", "password123", onSuccess, onError);

        verify(onError, timeout(ASYNC_TIMEOUT_MS)).accept(anyString());
        verifyNoInteractions(onSuccess);
    }

    @Test
    void logoutShouldDisconnectClearLocalUserAndResetState() {
        controller.logout();

        verify(connectionController).disconnect();
        verify(persistence).clearLocalUser();
        verify(stateModel).clearSession();
    }
}
