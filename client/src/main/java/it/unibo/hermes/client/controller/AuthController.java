package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.dto.LoginResponseDto;
import it.unibo.hermes.client.model.domain.AuthToken;
import it.unibo.hermes.client.model.domain.User;
import it.unibo.hermes.client.model.state.AuthState;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.service.LocalPersistenceService;
import it.unibo.hermes.client.service.RestAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.util.function.Consumer;

/**
 * Coordinates registration and login flows.
 */
@Component
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final RestAuthService authService;
    private final ClientStateModel stateModel;
    private final LocalPersistenceService persistence;
    private final ConnectionController connectionController;
    private final SyncController syncController;

    public AuthController(RestAuthService authService,
                          ClientStateModel stateModel,
                          LocalPersistenceService persistence,
                          ConnectionController connectionController,
                          SyncController syncController) {
        this.authService = authService;
        this.stateModel = stateModel;
        this.persistence = persistence;
        this.connectionController = connectionController;
        this.syncController = syncController;
    }

    /**
     * Initiates login.
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
                            onError.accept(friendlyError(err));
                        });
    }

    /**
     * Initiates registration.
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
                    onError.accept(friendlyError(err));
                })
                .subscribe();
    }

    /**
     * Logs the user out: disconnects WebSocket, clears state.
     */
    public void logout() {
        connectionController.disconnect();
        persistence.clearLocalUser();
        stateModel.clearSession();
    }

    private void handleLoginSuccess(LoginResponseDto resp) {
        AuthToken token = new AuthToken(resp.token(), resp.expiresAt());
        User user = new User(resp.username());

        stateModel.setAuthToken(token);
        stateModel.setCurrentUser(user);
        persistence.saveLocalUser(user);

        log.info("Authenticated as {}", user.username());

        // Open WebSocket, then sync once connected
        connectionController.setOnConnectedCallback(syncController::syncAll);
        connectionController.connect(token.rawToken());

        // Trigger navigation by setting AUTHENTICATED state
        stateModel.setAuthState(AuthState.AUTHENTICATED);
    }

    private String friendlyError(Throwable e) {
        String msg = e.getMessage();
        if (msg == null) return "Unknown error.";
        if (msg.contains("Invalid credentials") || msg.contains("401"))
            return "Invalid username or password.";
        if (msg.contains("connect") || msg.contains("refused"))
            return "Cannot reach the server. Check your connection.";
        return msg;
    }
}
