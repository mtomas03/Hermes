package it.unibo.hermes.client.view.auth;

import it.unibo.hermes.client.controller.AuthController;
import it.unibo.hermes.client.controller.NavigationController;

import javax.swing.*;
import java.awt.*;

/**
 * Abstract class containing shared GUI layout and styles for authentication screens.
 */
public abstract class AuthBaseView {

    protected final AuthController authController;
    protected final NavigationController navController;

    protected final Color COLOR_PRIMARY = new Color(33, 150, 243);
    protected final Color TEXT_DARK = new Color(33, 33, 33);
    protected final Color BORDER_COLOR = new Color(218, 224, 233);
    protected final Font MAIN_FONT = new Font("Segoe UI", Font.PLAIN, 14);

    public AuthBaseView(AuthController authController, NavigationController navController) {
        this.authController = authController;
        this.navController = navController;
    }

    /**
     * Template method to build the view structure.
     */
    public JPanel build() {
        JPanel root = new JPanel(new BorderLayout());

        // Center card
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        // Assemble shared structure
        card.add(buildHeader());
        card.add(Box.createVerticalStrut(28));

        // Delegate specific form fields creation to subclasses
        card.add(buildFormPanel());

        card.add(Box.createVerticalGlue());

        // Center the card in the root panel
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(card);

        root.add(centerPanel, BorderLayout.CENTER);
        return root;
    }

    private JPanel buildHeader() {
        JLabel logo = new JLabel("HERMES");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 30));
        logo.setForeground(COLOR_PRIMARY);
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel(getSubtitleText());
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(Color.GRAY);
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel headerPanel = new JPanel();
        headerPanel.setOpaque(false);
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.add(logo);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(subtitle);

        return headerPanel;
    }

    protected JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(TEXT_DARK);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    protected JTextField createTextField() {
        JTextField field = new JTextField(20);
        field.setFont(MAIN_FONT);
        field.setMaximumSize(new Dimension(280, 36));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        return field;
    }

    protected JPasswordField createPasswordField() {
        JPasswordField field = new JPasswordField(20);
        field.setFont(MAIN_FONT);
        field.setMaximumSize(new Dimension(280, 36));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        return field;
    }

    protected JButton createPrimaryButton(String text) {
        JButton btn = new JButton(text);
        btn.setBackground(COLOR_PRIMARY);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setMaximumSize(new Dimension(280, 40));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    protected JButton createLinkButton(String text) {
        JButton btn = new JButton(text);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setForeground(COLOR_PRIMARY);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        return btn;
    }

    protected void showPopup(Component parent, String message, String title, int type) {
        JOptionPane.showMessageDialog(parent, message, title, type);
    }

    protected abstract String getSubtitleText();

    protected abstract JPanel buildFormPanel();
}