package it.unibo.hermes.client.view.navigator;

import it.unibo.hermes.client.controller.AuthController;
import it.unibo.hermes.client.controller.ChatController;
import it.unibo.hermes.client.controller.NavigationController;
import it.unibo.hermes.client.model.state.ClientStateModel;
import it.unibo.hermes.client.view.auth.LoginView;
import it.unibo.hermes.client.view.auth.RegisterView;
import it.unibo.hermes.client.view.chat.ChatView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;

/**
 * Swing implementation of {@link ViewNavigator}.
 */
@Component
public class SwingViewNavigator implements ViewNavigator {

    private static final Logger log = LoggerFactory.getLogger(SwingViewNavigator.class);

    private final ClientStateModel stateModel;
    private final AuthController authController;
    private final ChatController chatController;
    private final NavigationController navigationController;

    private JFrame primaryFrame;
    private JPanel cardPanel;
    private CardLayout cardLayout;

    public SwingViewNavigator(ClientStateModel stateModel,
                              AuthController authController,
                              ChatController chatController,
                              @Lazy NavigationController navigationController) {
        this.stateModel = stateModel;
        this.authController = authController;
        this.chatController = chatController;
        this.navigationController = navigationController;
    }

    @Override
    public void showLogin() {
        SwingUtilities.invokeLater(() -> {
            LoginView view = new LoginView(authController, navigationController);
            switchCard("LOGIN", view.build());
            log.info("Navigating -> 'Login'");
        });
    }

    @Override
    public void showRegister() {
        SwingUtilities.invokeLater(() -> {
            RegisterView view = new RegisterView(authController, navigationController);
            switchCard("REGISTER", view.build());
            log.info("Navigating -> 'Register'");
        });
    }

    @Override
    public void showChat() {
        SwingUtilities.invokeLater(() -> {
            ChatView view = new ChatView(stateModel, chatController, authController);
            switchCard("CHAT", view.build());
            log.info("Navigating -> 'Chat'");
        });
    }

    @Override
    public void showSessionExpiredDialog() {
        SwingUtilities.invokeLater(() -> {
            JOptionPane.showMessageDialog(
                    primaryFrame,
                    "Your session has expired. Please log in again.",
                    "Session expired",
                    JOptionPane.WARNING_MESSAGE
            );
            showLogin();
        });
    }

    @Override
    public void initPrimaryFrame() {
        this.primaryFrame = new JFrame("Hermes");
        this.primaryFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.primaryFrame.setSize(800, 560);
        this.primaryFrame.setMinimumSize(new Dimension(800, 560));
        this.primaryFrame.setLocationRelativeTo(null);

        this.cardLayout = new CardLayout();
        this.cardPanel = new JPanel(cardLayout);
        this.primaryFrame.add(cardPanel);
    }

    @Override
    public void setPrimaryFrameVisible(boolean b) {
        if (primaryFrame != null) {
            primaryFrame.setVisible(b);
        }
    }

    private void switchCard(String cardName, JPanel panel) {
        if (cardPanel != null && cardLayout != null) {
            cardPanel.removeAll();
            cardPanel.add(panel, cardName);
            cardLayout.show(cardPanel, cardName);
            cardPanel.revalidate();
            cardPanel.repaint();
        }
    }
}
