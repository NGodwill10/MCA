package ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import services.AuthService;

public final class ChangePasswordDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final transient AuthService authService;
    private final JPasswordField currentPassword = new JPasswordField(20);
    private final JPasswordField newPassword = new JPasswordField(20);
    private final JPasswordField confirmation = new JPasswordField(20);
    private final JLabel feedback = new JLabel(" ");

    public ChangePasswordDialog(java.awt.Window owner, AuthService authService) {
        super(owner, "Change password", Dialog.ModalityType.APPLICATION_MODAL);
        this.authService = authService;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        add(buildFields(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(owner);
        SwingUtilities.invokeLater(currentPassword::requestFocusInWindow);
    }

    private JPanel buildFields() {
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setBorder(BorderFactory.createEmptyBorder(14, 16, 6, 16));
        addField(fields, 0, "Current password", currentPassword);
        addField(fields, 1, "New password", newPassword);
        addField(fields, 2, "Confirm new password", confirmation);
        feedback.setForeground(Theme.ERROR);
        GridBagConstraints constraints = constraints(0, 3);
        constraints.gridwidth = 2;
        fields.add(feedback, constraints);
        return fields;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton save = new JButton("Change password");
        save.addActionListener(event -> changePassword());
        getRootPane().setDefaultButton(save);
        actions.add(cancel);
        actions.add(save);
        return actions;
    }

    private void changePassword() {
        char[] current = currentPassword.getPassword();
        char[] replacement = newPassword.getPassword();
        char[] confirmed = confirmation.getPassword();
        try {
            authService.changePassword(current, replacement, confirmed);
            JOptionPane.showMessageDialog(this, "Password changed.", "Account", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        } catch (IllegalArgumentException exception) {
            feedback.setText(exception.getMessage());
        } catch (IOException exception) {
            System.getLogger(ChangePasswordDialog.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to update account credentials", exception);
            feedback.setText("The password could not be saved. Check local storage and try again.");
        } finally {
            Arrays.fill(current, '\0');
            Arrays.fill(replacement, '\0');
            Arrays.fill(confirmed, '\0');
            currentPassword.setText("");
            newPassword.setText("");
            confirmation.setText("");
        }
    }

    private static void addField(JPanel panel, int row, String labelText, JPasswordField field) {
        JLabel label = new JLabel(labelText);
        label.setLabelFor(field);
        panel.add(label, constraints(0, row));
        GridBagConstraints input = constraints(1, row);
        input.fill = GridBagConstraints.HORIZONTAL;
        input.weightx = 1;
        panel.add(field, input);
    }

    private static GridBagConstraints constraints(int column, int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = column;
        constraints.gridy = row;
        constraints.anchor = GridBagConstraints.LINE_START;
        constraints.insets = new Insets(6, 6, 6, 6);
        return constraints;
    }
}