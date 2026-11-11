package it.unibo.hermes.client.view.auth;

import it.unibo.hermes.client.controller.AuthController;
import it.unibo.hermes.client.controller.NavigationController;

import javax.swing.*;
import java.awt.*;

public class LoginView extends AuthBaseView {

    public LoginView(AuthController authController, NavigationController navController) {
        super(authController, navController);
    }

    @Override
    protected String getSubtitleText() {
        return "Log in";
    }

    @Override
    protected JPanel buildFormPanel() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JTextField usernameField = createTextField();
        JPasswordField passwordField = createPasswordField();
        JButton loginBtn = createPrimaryButton("Log in");

        JButton registerBtn = createLinkButton("Don't have an account? Register");

        // Layout assembly
        form.add(createLabel("Username"));
        form.add(Box.createVerticalStrut(4));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(12));
        form.add(createLabel("Password"));
        form.add(Box.createVerticalStrut(4));
        form.add(passwordField);
        form.add(Box.createVerticalStrut(24));
        form.add(loginBtn);
        form.add(Box.createVerticalStrut(4));
        form.add(registerBtn);
        form.setMaximumSize(new Dimension(280, Integer.MAX_VALUE));

        // Actions
        Runnable doLogin = () -> {
            String u = usernameField.getText().trim();
            String p = new String(passwordField.getPassword());
            if (u.isEmpty() || p.isEmpty()) {
                showPopup(form, "Please enter username and password!", "Missing Fields", JOptionPane.WARNING_MESSAGE);
                return;
            }
            loginBtn.setEnabled(false);
            authController.login(u, p, err -> {
                loginBtn.setEnabled(true);
                showPopup(form, err, "Login Error", JOptionPane.ERROR_MESSAGE);
            });
        };

        loginBtn.addActionListener(e -> doLogin.run());
        passwordField.addActionListener(e -> doLogin.run());
        registerBtn.addActionListener(e -> navController.showRegister());

        return form;
    }
}