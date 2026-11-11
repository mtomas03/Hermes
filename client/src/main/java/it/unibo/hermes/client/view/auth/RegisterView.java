package it.unibo.hermes.client.view.auth;

import it.unibo.hermes.client.controller.AuthController;
import it.unibo.hermes.client.controller.NavigationController;

import javax.swing.*;
import java.awt.*;

public class RegisterView extends AuthBaseView {

    public RegisterView(AuthController authController, NavigationController navController) {
        super(authController, navController);
    }

    @Override
    protected String getSubtitleText() {
        return "Create your account";
    }

    @Override
    protected JPanel buildFormPanel() {
        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        JTextField usernameField = createTextField();
        JPasswordField passwordField = createPasswordField();
        JPasswordField confirmField = createPasswordField();
        JButton regBtn = createPrimaryButton("Register");

        JButton backBtn = createLinkButton("Already have an account? Log in");

        // Layout assembly
        form.add(createLabel("Username"));
        form.add(Box.createVerticalStrut(4));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(12));
        form.add(createLabel("Password"));
        form.add(Box.createVerticalStrut(4));
        form.add(passwordField);
        form.add(Box.createVerticalStrut(12));
        form.add(createLabel("Confirm Password"));
        form.add(Box.createVerticalStrut(4));
        form.add(confirmField);
        form.add(Box.createVerticalStrut(24));
        form.add(regBtn);
        form.add(Box.createVerticalStrut(4));
        form.add(backBtn);
        form.setMaximumSize(new Dimension(280, Integer.MAX_VALUE));

        // Actions
        regBtn.addActionListener(e -> {
            String u = usernameField.getText().trim();
            String p = new String(passwordField.getPassword());
            String p2 = new String(confirmField.getPassword());

            if (u.isEmpty() || p.isEmpty() || p2.isEmpty()) {
                showPopup(form, "All fields are required.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!p.equals(p2)) {
                showPopup(form, "Passwords do not match.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (p.length() < 8) {
                showPopup(form, "Password must be at least 8 characters.", "Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            regBtn.setEnabled(false);

            authController.register(u, p,
                    () -> {
                        regBtn.setEnabled(true);
                        showPopup(form, "Account created! Redirecting", "Success", JOptionPane.INFORMATION_MESSAGE);
                        Timer timer = new Timer(1000, evt -> navController.showLogin());
                        timer.setRepeats(false);
                        timer.start();
                    },
                    err -> {
                        regBtn.setEnabled(true);
                        showPopup(form, err, "Register Error", JOptionPane.ERROR_MESSAGE);
                    });
        });

        backBtn.addActionListener(e -> navController.showLogin());

        return form;
    }
}