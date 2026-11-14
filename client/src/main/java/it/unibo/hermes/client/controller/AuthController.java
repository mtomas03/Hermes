package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.infrastructure.exception.AuthException;
import it.unibo.hermes.client.model.domain.AuthToken;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.dto.AuthResponseDto;
import it.unibo.hermes.client.model.service.AuthService;
import it.unibo.hermes.client.model.service.LocalPersistenceService;
import it.unibo.hermes.client.model.service.MessageService;
import it.unibo.hermes.client.model.state.AuthState;
import it.unibo.hermes.client.model.state.ClientStateModel;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.scheduler.Schedulers;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Handles user authentication: login, registration, logout
 * and session management.
 */
@Component
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final ClientStateModel stateModel;
    private final LocalPersistenceService persistence;
    private final ConnectionController connectionController;
    private final SyncController syncController;
    private final MessageService messageService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "auth-expiry");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> expirationAuthTokenTask;

    public AuthController(AuthService authService,
                          ClientStateModel stateModel,
                          LocalPersistenceService persistence,
                          ConnectionController connectionController,
                          SyncController syncController) {
        this(authService, stateModel, persistence, connectionController, syncController, null);
    }

    @Autowired
    public AuthController(AuthService authService,
                          ClientStateModel stateModel,
                          LocalPersistenceService persistence,
                          ConnectionController connectionController,
                          SyncController syncController,
                          MessageService messageService) {
        this.authService = authService;
        this.stateModel = stateModel;
        this.persistence = persistence;
        this.connectionController = connectionController;
        this.syncController = syncController;
        this.messageService = messageService;
    }

    /**
     * Attempts to log in with the provided credentials.
     *
     * @param username the username to log in with
     * @param password the password to log in with
     * @param onError  callback invoked with a user-friendly error message if login fails
     */
    public void login(String username, String password, Consumer<String> onError) {
        stateModel.setAuthState(AuthState.AUTHENTICATING);
        stateModel.setStatusMessage("Logging in");

        authService.login(username, password)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        this::handleLoginSuccess,
                        err -> {
                            log.warn("Login failed: {}", err.getMessage());
                            stateModel.setAuthState(AuthState.UNAUTHENTICATED);
                            stateModel.setStatusMessage("Login failed");
                            onError.accept(userFriendlyError(err));
                        });
    }

    /**
     * Attempts to register a new account with the provided credentials.
     *
     * @param username  the desired username
     * @param password  the desired password
     * @param onSuccess callback invoked if registration succeeds
     * @param onError   callback invoked with a user-friendly error message if registration fails
     */
    public void register(String username, String password,
                         Runnable onSuccess, Consumer<String> onError) {
        stateModel.setStatusMessage("Registering");

        authService.register(username, password)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(v -> {
                    log.info("Registration successful for {}", username);
                    stateModel.setStatusMessage("Account created");
                    onSuccess.run();
                })
                .doOnError(err -> {
                    log.warn("Registration failed: {}", err.getMessage());
                    stateModel.setStatusMessage("Registration failed");
                    onError.accept(userFriendlyError(err));
                })
                .subscribe();
    }

    /**
     * Logs the user out.
     */
    public void logout() {
        if (expirationAuthTokenTask != null) {
            expirationAuthTokenTask.cancel(false);
        }
        syncController.resetSyncState();
        Schedulers.boundedElastic().schedule(() -> {
            try {
                persistence.clearAuthToken();
            } finally {
                try {
                    connectionController.disconnect();
                } catch (RuntimeException e) {
                    log.warn("Error while closing the connection during logout: {}", e.getMessage());
                }
                stateModel.clearSession();
                persistence.clearLocalUser();
                syncController.resetSyncState();
                if (messageService != null) {
                    messageService.resetClocks();
                }
                stateModel.setStatusMessage("Logged out");
            }
        });
    }

    /**
     * Restores the previous session on application start,
     * if a non-expired token and the local user are stored.
     *
     * @return {@code true} if a session was restored
     */
    public boolean restoreSession() {
        try {
            Optional<AuthToken> token = persistence.loadValidAuthToken();
            if (token.isEmpty()) {
                return false;
            }
            Optional<User> user = persistence.loadLocalUser();
            if (user.isEmpty()) {
                persistence.clearAuthToken();
                return false;
            }
            log.info("Restoring session for {}", user.get().username());
            startSession(token.get(), user.get());
            stateModel.setStatusMessage("Session restored");
            return true;
        } catch (Exception e) {
            log.error("Failed to restore previous session", e);
            stateModel.setAuthState(AuthState.UNAUTHENTICATED);
            stateModel.setStatusMessage("Session restore failed");
            return false;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (expirationAuthTokenTask != null) {
            expirationAuthTokenTask.cancel(false);
        }
        scheduler.shutdownNow();
    }

    private void handleLoginSuccess(AuthResponseDto resp) {
        Instant expiresAt = Instant.now().plusMillis(resp.expiresInMs());
        AuthToken token = new AuthToken(resp.token(), expiresAt);
        User user = new User(resp.username());

        Optional<User> existingUser = persistence.loadLocalUser();
        if (existingUser.isPresent() && !existingUser.get().username().equals(user.username())) {
            syncController.resetSyncState();
            persistence.clearLocalUser();
            if (messageService != null) {
                messageService.resetClocks();
            }
        }

        persistence.saveAuthToken(token);
        persistence.saveLocalUser(user);
        startSession(token, user);
        stateModel.setStatusMessage("Logged in");
    }

    private void startSession(AuthToken token, User user) {
        stateModel.setAuthToken(token);
        stateModel.setCurrentUser(user);

        log.info("Authenticated as {}", user.username());

        if (expirationAuthTokenTask != null) {
            expirationAuthTokenTask.cancel(false);
        }
        long delayMs = Math.max(0, Duration.between(Instant.now(), token.expiresAt()).toMillis());
        expirationAuthTokenTask = scheduler.schedule(this::handleTokenExpired, delayMs, TimeUnit.MILLISECONDS);

        connectionController.setOnConnectedCallback(syncController::syncAll);
        connectionController.connect(token.rawToken());

        stateModel.setAuthState(AuthState.AUTHENTICATED);
    }

    private void handleTokenExpired() {
        log.info("JWT expired, terminating session");
        syncController.resetSyncState();
        try {
            persistence.clearAuthToken();
        } finally {
            try {
                connectionController.disconnect();
            } catch (RuntimeException e) {
                log.warn("Error while closing connection on token expiry: {}", e.getMessage());
            }
            stateModel.clearSession();
            persistence.clearLocalUser();
            syncController.resetSyncState();
            if (messageService != null) {
                messageService.resetClocks();
            }
            stateModel.setAuthState(AuthState.TOKEN_EXPIRED);
            stateModel.setStatusMessage("Session expired. Please log in again.");
        }
    }

    private String userFriendlyError(Throwable e) {
        if (e == null) {
            return "Unknown error.";
        }
        Throwable cause = Exceptions.unwrap(e);

        if (cause instanceof AuthException authEx) {
            return switch (authEx.getReason()) {
                case SERVICE_UNAVAILABLE -> "The service is temporarily unavailable. Please try again later.";
                case USERNAME_TAKEN -> "This username is already taken.";
                case INVALID_CREDENTIALS -> "Invalid username or password.";
                case NETWORK_ERROR -> "Cannot reach the server. Check your connection.";
                case BAD_REQUEST -> getAuthExceptionDetails(authEx);
                case SERVER_ERROR -> "A server error occurred. Please try again later.";
            };
        }

        if (cause instanceof WebClientResponseException responseEx) {
            int status = responseEx.getStatusCode().value();
            if (status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                return "The service is temporarily unavailable. Please try again later.";
            }
            if (status == HttpStatus.CONFLICT.value()) {
                return "This username is already taken.";
            }
            if (status == HttpStatus.UNAUTHORIZED.value()) {
                return "Invalid username or password.";
            }
            if (responseEx.getStatusCode().is5xxServerError()) {
                return "A server error occurred. Please try again later.";
            }
            return "Request failed (" + status + ").";
        }

        if (cause instanceof WebClientRequestException
                || hasCause(cause, ConnectException.class)
                || hasCause(cause, UnknownHostException.class)
                || hasCause(cause, SocketTimeoutException.class)
                || hasCause(cause, TimeoutException.class)) {
            return "Cannot reach the server. Check your connection.";
        }

        return cause.getMessage() != null ? cause.getMessage() : "Unknown error.";
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String getAuthExceptionDetails(AuthException authEx) {
        String result;
        String message = authEx.getMessage();
        if (message == null || message.isBlank()) {
            result = "Invalid registration details.";
        } else if (message.startsWith("Registration failed: ") && message.length() > "Registration failed: ".length()) {
            result = message.substring("Registration failed: ".length());
        } else {
            result = message;
        }
        return result;
    }
}
