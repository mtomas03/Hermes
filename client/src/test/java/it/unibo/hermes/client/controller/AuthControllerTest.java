package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.model.domain.AuthToken;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.dto.AuthResponseDto;
import it.unibo.hermes.client.model.service.AuthService;
import it.unibo.hermes.client.model.service.LocalPersistenceService;
import it.unibo.hermes.client.model.service.MessageService;
import it.unibo.hermes.client.model.state.AuthState;
import it.unibo.hermes.client.model.state.ClientStateModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    private MessageService messageService;
    @Mock
    private Consumer<String> onError;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(
                authService, stateModel, persistence, connectionController, syncController, messageService);
    }

    @Test
    void successfulLoginShouldStoreTokenAndUserAndAuthenticate() {
        AuthResponseDto response = new AuthResponseDto("jwt-token", "alice", 3_600_000);
        when(persistence.loadLocalUser()).thenReturn(Optional.empty());
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

        verify(connectionController, timeout(ASYNC_TIMEOUT_MS)).disconnect();
        verify(persistence, timeout(ASYNC_TIMEOUT_MS)).clearLocalUser();
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).clearSession();
    }

    @Test
    void successfulLoginShouldPersistTheToken() {
        when(persistence.loadLocalUser()).thenReturn(Optional.empty());
        when(authService.login("alice", "secret"))
                .thenReturn(Mono.just(new AuthResponseDto("jwt-token", "alice", 3_600_000)));

        controller.login("alice", "secret", onError);

        verify(persistence, timeout(ASYNC_TIMEOUT_MS)).saveAuthToken(any(AuthToken.class));
    }

    @Test
    void restoreSessionShouldReuseValidStoredToken() {
        AuthToken token = new AuthToken("stored-jwt", Instant.now().plusSeconds(600));
        when(persistence.loadValidAuthToken()).thenReturn(Optional.of(token));
        when(persistence.loadLocalUser()).thenReturn(Optional.of(new User("alice")));

        boolean restored = controller.restoreSession();

        assertTrue(restored);
        verify(stateModel).setAuthToken(token);
        verify(connectionController).connect("stored-jwt");
        verify(stateModel).setAuthState(AuthState.AUTHENTICATED);
        verifyNoInteractions(authService);
    }

    @Test
    void restoreSessionShouldDoNothingWithoutValidToken() {
        when(persistence.loadValidAuthToken()).thenReturn(Optional.empty());

        assertFalse(controller.restoreSession());

        verify(stateModel, never()).setAuthState(AuthState.AUTHENTICATED);
        verifyNoInteractions(connectionController);
    }

    @Test
    void restoreSessionShouldDropTokenWhenLocalUserIsMissing() {
        when(persistence.loadValidAuthToken())
                .thenReturn(Optional.of(new AuthToken("t", Instant.now().plusSeconds(60))));
        when(persistence.loadLocalUser()).thenReturn(Optional.empty());

        assertFalse(controller.restoreSession());

        verify(persistence).clearAuthToken();
        verifyNoInteractions(connectionController);
    }

    @Test
    void logoutShouldClearPersistedTokenFirstAndNotDeleteHistory() {
        controller.logout();

        verify(persistence, timeout(ASYNC_TIMEOUT_MS)).clearAuthToken();
        verify(connectionController, timeout(ASYNC_TIMEOUT_MS)).disconnect();
        verify(persistence, never()).saveMessage(any());
        verify(persistence, never()).saveConversation(any());
    }

    @Test
    void logoutShouldCompleteEvenIfDisconnectFails() {
        doThrow(new IllegalStateException("socket error")).when(connectionController).disconnect();

        controller.logout();

        verify(persistence, timeout(ASYNC_TIMEOUT_MS)).clearAuthToken();
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).clearSession();
        verify(syncController, timeout(ASYNC_TIMEOUT_MS).atLeastOnce()).resetSyncState();
        verify(messageService, timeout(ASYNC_TIMEOUT_MS)).resetClocks();
    }

    @Test
    void loginWithDifferentUserShouldClearPreviousLocalUser() {
        when(persistence.loadLocalUser()).thenReturn(Optional.of(new User("alice")));
        AuthResponseDto response = new AuthResponseDto("jwt-token-bob", "bob", 3_600_000);
        when(authService.login("bob", "secret")).thenReturn(Mono.just(response));

        controller.login("bob", "secret", onError);

        verify(persistence, timeout(ASYNC_TIMEOUT_MS)).clearLocalUser();
        verify(persistence, timeout(ASYNC_TIMEOUT_MS)).saveLocalUser(new User("bob"));
        verify(connectionController, timeout(ASYNC_TIMEOUT_MS)).connect("jwt-token-bob");
        verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).setAuthState(AuthState.AUTHENTICATED);
    }

    @Test
    void expiredTokenShouldTriggerTokenExpiredStateAndDisconnect() throws InterruptedException {
        when(persistence.loadLocalUser()).thenReturn(Optional.empty());
        AuthResponseDto response = new AuthResponseDto("jwt-token", "alice", 50);
        when(authService.login("alice", "secret")).thenReturn(Mono.just(response));

        controller.login("alice", "secret", onError);
        Thread.sleep(150);

        verify(connectionController).disconnect();
        verify(persistence).clearAuthToken();
        verify(stateModel).clearSession();
        verify(stateModel).setAuthState(AuthState.TOKEN_EXPIRED);
    }

    @Test
    void logoutShouldDisconnectAndClearUserBeforeResettingLamportClocks() {
        controller.logout();

        InOrder order = inOrder(connectionController, persistence, stateModel, messageService);
        order.verify(connectionController, timeout(ASYNC_TIMEOUT_MS)).disconnect();
        order.verify(stateModel, timeout(ASYNC_TIMEOUT_MS)).clearSession();
        order.verify(persistence, timeout(ASYNC_TIMEOUT_MS)).clearLocalUser();
        order.verify(messageService, timeout(ASYNC_TIMEOUT_MS)).resetClocks();
    }
}
