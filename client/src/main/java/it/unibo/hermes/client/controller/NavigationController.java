package it.unibo.hermes.client.controller;

import it.unibo.hermes.client.model.state.AuthState;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.view.ChatView;
import it.unibo.hermes.client.view.auth.LoginView;
import it.unibo.hermes.client.view.auth.RegisterView;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;

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
    private final AuthController authController;
    private final ChatController chatController;

    private JPanel cardPanel;
    private CardLayout cardLayout;

    public NavigationController(ClientStateModel stateModel,
                                AuthController authController,
                                ChatController chatController) {
        this.stateModel = stateModel;
        this.authController = authController;
        this.chatController = chatController;
    }

    /**
     * Registers a listener on authState so navigation
     * follows state automatically.
     */
    @PostConstruct
    void bindToAuthState() {
        stateModel.addAuthStateListener(evt -> {
            AuthState state = (AuthState) evt.getNewValue();
            if (state == AuthState.AUTHENTICATED) {
                log.info("Auth state -> AUTHENTICATED: showing chat");
                showChat();
            } else if (state == AuthState.UNAUTHENTICATED) {
                log.info("Auth state -> UNAUTHENTICATED: showing login");
                showLogin();
            } else if (state == AuthState.TOKEN_EXPIRED) {
                log.warn("Auth token expired: returning to login");
                showLogin();
            }
        });
    }

    public void initPrimaryFrame(JFrame frame) {
        this.cardLayout = new CardLayout();
        this.cardPanel = new JPanel(cardLayout);
        frame.add(cardPanel);
    }

    public void showLogin() {
        LoginView view = new LoginView(authController, this);
        JPanel panel = view.build();

        String cardName = "LOGIN";
        if (cardPanel.getComponentCount() > 0 && cardPanel.getComponent(0) instanceof JPanel) {
            cardPanel.removeAll();
        }
        cardPanel.add(panel, cardName);
        cardLayout.show(cardPanel, cardName);
        cardPanel.revalidate();
        cardPanel.repaint();

        log.info("Navigating -> 'Login'");
    }

    public void showRegister() {
        RegisterView view = new RegisterView(authController, this);
        JPanel panel = view.build();

        String cardName = "REGISTER";
        cardPanel.removeAll();
        cardPanel.add(panel, cardName);
        cardLayout.show(cardPanel, cardName);
        cardPanel.revalidate();
        cardPanel.repaint();

        log.info("Navigating -> 'Register'");
    }

    public void showChat() {
        ChatView view = new ChatView(stateModel, chatController, authController);
        JPanel panel = view.build();

        String cardName = "CHAT";
        cardPanel.removeAll();
        cardPanel.add(panel, cardName);
        cardLayout.show(cardPanel, cardName);
        cardPanel.revalidate();
        cardPanel.repaint();

        log.info("Navigating -> 'Chat'");
    }
}
