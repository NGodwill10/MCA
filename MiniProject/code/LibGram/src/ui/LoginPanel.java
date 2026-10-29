package ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import model.User;
import services.AuthService;

public final class LoginPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient AuthService authService;
    private final transient Consumer<User> onAuthenticated;
    private final boolean initialSetup;
    private final JTextField usernameField = new JTextField(24);
    private final JTextField displayNameField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JLabel feedback = new JLabel(" ", SwingConstants.CENTER);
    private final JButton submitButton = new JButton();

    public LoginPanel(AuthService authService, Consumer<User> onAuthenticated) {
        this.authService = authService;
        this.onAuthenticated = onAuthenticated;
        this.initialSetup = !authService.hasAccounts();
        buildPanel();
    }

    private void buildPanel() {
        setLayout(new GridBagLayout());
        setBackground(Theme.PAPER);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                BorderFactory.createEmptyBorder(30, 36, 26, 36)));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(8, 0, 8, 0);

        JLabel heading = new JLabel(initialSetup ? "Create admin account" : "Welcome to LibGram",
                SwingConstants.CENTER);
        heading.setFont(Theme.HEADING);
        heading.setForeground(Theme.INK);
        form.add(heading, constraints);
        constraints.gridy++;
        JLabel subtitle = new JLabel(initialSetup ? "Set up the first administrator" : "Sign in to continue",
                SwingConstants.CENTER);
        subtitle.setForeground(Theme.MUTED);
        form.add(subtitle, constraints);

        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.insets = new Insets(8, 0, 2, 10);
        form.add(label("Username", usernameField), constraints);
        constraints.gridx = 1;
        constraints.insets = new Insets(8, 0, 2, 0);
        usernameField.setName("username");
        form.add(usernameField, constraints);

        if (initialSetup) {
            constraints.gridx = 0;
            constraints.gridy++;
            constraints.insets = new Insets(8, 0, 2, 10);
            form.add(label("Display name", displayNameField), constraints);
            constraints.gridx = 1;
            constraints.insets = new Insets(8, 0, 2, 0);
            displayNameField.setName("displayName");
            form.add(displayNameField, constraints);
        }

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.insets = new Insets(8, 0, 2, 10);
        form.add(label("Password", passwordField), constraints);
        constraints.gridx = 1;
        constraints.insets = new Insets(8, 0, 2, 0);
        passwordField.setName("password");
        form.add(passwordField, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.insets = new Insets(12, 0, 4, 0);
        feedback.setForeground(Theme.ERROR);
        form.add(feedback, constraints);
        constraints.gridy++;
        submitButton.setText(initialSetup ? "Create administrator" : "Login");
        submitButton.setBackground(Theme.ACCENT);
        submitButton.setForeground(Theme.WHITE);
        submitButton.setFocusPainted(false);
        submitButton.addActionListener(event -> submit());
        form.add(submitButton, constraints);
        usernameField
                .addActionListener(event -> (initialSetup ? displayNameField : passwordField).requestFocusInWindow());
        displayNameField.addActionListener(event -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(event -> submit());
        add(form, new GridBagConstraints());
    }

    private void submit() {
        if (!submitButton.isEnabled()) {
            return;
        }
        char[] password = passwordField.getPassword();
        try {
            if (initialSetup) {
                User admin = authService.createInitialAdmin(
                        usernameField.getText(), displayNameField.getText(), password);
                onAuthenticated.accept(admin);
            } else {
                Optional<User> user = authService.authenticate(usernameField.getText(), password);
                if (user.isPresent()) {
                    onAuthenticated.accept(user.get());
                } else {
                    showLoginFailure();
                }
            }
        } catch (IllegalArgumentException exception) {
            feedback.setText(exception.getMessage());
        } catch (IOException exception) {
            System.getLogger(LoginPanel.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to save administrator account", exception);
            feedback.setText("The account could not be saved. Check local storage and try again.");
        } finally {
            Arrays.fill(password, '\0');
            passwordField.setText("");
        }
    }

    private void showLoginFailure() {
        long lockout = authService.lockoutMillisRemaining();
        if (lockout == 0) {
            feedback.setText("Incorrect username or password");
            return;
        }
        Timer timer = new Timer(150, null);
        timer.addActionListener(event -> {
            long remaining = authService.lockoutMillisRemaining();
            if (remaining <= 0) {
                timer.stop();
                submitButton.setEnabled(true);
                feedback.setText("Incorrect username or password");
            } else {
                feedback.setText("Incorrect username or password. Try again in "
                        + ((remaining + 999) / 1000) + " seconds.");
            }
        });
        submitButton.setEnabled(false);
        feedback.setText("Incorrect username or password. Try again in "
                + ((lockout + 999) / 1000) + " seconds.");
        timer.start();
    }

    private static JLabel label(String text, java.awt.Component target) {
        JLabel label = new JLabel(text);
        label.setForeground(Theme.INK);
        label.setLabelFor(target);
        return label;
    }
}