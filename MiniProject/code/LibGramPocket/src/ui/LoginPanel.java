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
    private final JTextField usernameField = new JTextField(24);
    private final JTextField displayNameField = new JTextField(24);
    private final JPasswordField passwordField = new JPasswordField(24);
    private final JPasswordField confirmationField = new JPasswordField(24);
    private final JLabel feedback = new JLabel(" ", SwingConstants.CENTER);
    private final JPanel form = new JPanel(new GridBagLayout());
    private boolean signupMode;
    private JButton submitButton;

    public LoginPanel(AuthService authService, Consumer<User> onAuthenticated) {
        this.authService = authService;
        this.onAuthenticated = onAuthenticated;
        setLayout(new GridBagLayout());
        setBackground(Theme.PAPER);
        form.setBackground(Theme.WHITE);
        form.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                BorderFactory.createEmptyBorder(30, 36, 26, 36)));
        add(form, new GridBagConstraints());
        buildForm();
    }

    private void buildForm() {
        form.removeAll();
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(8, 0, 8, 0);

        JLabel heading = new JLabel(signupMode ? "Create your account" : "Your books, in your pocket",
                SwingConstants.CENTER);
        heading.setFont(Theme.HEADING);
        heading.setForeground(Theme.INK);
        form.add(heading, constraints);
        constraints.gridy++;
        JLabel subtitle = new JLabel(
                signupMode ? "Your collection stays private on this device" : "Sign in to continue",
                SwingConstants.CENTER);
        subtitle.setForeground(Theme.MUTED);
        form.add(subtitle, constraints);

        constraints.gridy++;
        addField("Username", usernameField, form, constraints);
        if (signupMode) {
            constraints.gridy++;
            addField("Display name (optional)", displayNameField, form, constraints);
        }
        constraints.gridy++;
        addField("Password", passwordField, form, constraints);
        if (signupMode) {
            constraints.gridy++;
            addField("Confirm password", confirmationField, form, constraints);
        }

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.insets = new Insets(12, 0, 4, 0);
        feedback.setForeground(Theme.ERROR);
        form.add(feedback, constraints);
        constraints.gridy++;
        submitButton = new JButton(signupMode ? "Sign up" : "Login");
        submitButton.setBackground(Theme.ACCENT);
        submitButton.setForeground(Theme.WHITE);
        submitButton.setFocusPainted(false);
        submitButton.addActionListener(event -> submit());
        form.add(submitButton, constraints);
        constraints.gridy++;
        JButton modeButton = new JButton(signupMode ? "Already have an account? Login" : "Create account");
        modeButton.setFocusPainted(false);
        modeButton.addActionListener(event -> {
            signupMode = !signupMode;
            feedback.setText(" ");
            buildForm();
        });
        form.add(modeButton, constraints);

        usernameField
                .addActionListener(event -> (signupMode ? displayNameField : passwordField).requestFocusInWindow());
        displayNameField.addActionListener(event -> passwordField.requestFocusInWindow());
        passwordField.addActionListener(event -> {
            if (signupMode) {
                confirmationField.requestFocusInWindow();
            } else {
                submit();
            }
        });
        confirmationField.addActionListener(event -> submit());
        form.revalidate();
        form.repaint();
    }

    private void submit() {
        if (!submitButton.isEnabled()) {
            return;
        }
        char[] password = passwordField.getPassword();
        char[] confirmation = confirmationField.getPassword();
        try {
            if (signupMode) {
                User user = authService.signUp(usernameField.getText(), displayNameField.getText(), password,
                        confirmation);
                onAuthenticated.accept(user);
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
                    "Unable to save account", exception);
            feedback.setText("The account could not be saved. Check local storage and try again.");
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            passwordField.setText("");
            confirmationField.setText("");
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

    private static void addField(String text, java.awt.Component input, JPanel panel,
            GridBagConstraints constraints) {
        constraints.gridx = 0;
        constraints.gridwidth = 1;
        constraints.insets = new Insets(8, 0, 2, 10);
        JLabel label = new JLabel(text);
        label.setForeground(Theme.INK);
        label.setLabelFor(input);
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.insets = new Insets(8, 0, 2, 0);
        panel.add(input, constraints);
    }
}