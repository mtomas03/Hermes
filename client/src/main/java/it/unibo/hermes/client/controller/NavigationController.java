package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.model.state.AuthState;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.view.navigator.ViewNavigator;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Manages view transitions for the primary window.
 *
 * <p>Navigation is driven reactively: this controller listens to
 * {@link ClientStateModel} auth state and routes the user automatically
 * on state changes, so no controller needs to call navigation methods directly.
 */
@Component
public class NavigationController {

    private static final Logger log = LoggerFactory.getLogger(NavigationController.class);

    private final ClientStateModel stateModel;
    private final ViewNavigator viewNavigator;

    public NavigationController(ClientStateModel stateModel, ViewNavigator viewNavigator) {
        this.stateModel = stateModel;
        this.viewNavigator = viewNavigator;
    }

    @PostConstruct
    void bindToAuthState() {
        stateModel.addAuthStateListener(evt -> {
            AuthState state = (AuthState) evt.getNewValue();
            if (state == AuthState.AUTHENTICATED) {
                log.info("Auth state -> AUTHENTICATED: routing to chat");
                viewNavigator.showChat();
            } else if (state == AuthState.UNAUTHENTICATED) {
                log.info("Auth state -> UNAUTHENTICATED: routing to login");
                viewNavigator.showLogin();
            } else if (state == AuthState.TOKEN_EXPIRED) {
                log.warn("Auth token expired: notifying view");
                viewNavigator.showSessionExpiredDialog();
            }
        });
    }

    public void showLogin() {
        viewNavigator.showLogin();
    }

    public void showRegister() {
        viewNavigator.showRegister();
    }

    public void showChat() {
        viewNavigator.showChat();
    }

    public void initPrimaryFrame() {
        viewNavigator.initPrimaryFrame();
    }

    public void setPrimaryFrameVisible(boolean b) {
        viewNavigator.setPrimaryFrameVisible(b);
    }
}
