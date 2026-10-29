package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import model.User;
import services.PermissionPolicy;
import services.AccountService;
import services.SettingsService;
import services.AlertScanner;
import data.StoragePaths;

public final class TitleBar extends JPanel {
    private static final long serialVersionUID = 1L;

    public TitleBar(Window owner, User user, PermissionPolicy permissions, SettingsService settings,
            AccountService accounts,
            Runnable onLogout, AlertScanner alertScanner) {
        setLayout(new BorderLayout());
        setBackground(Theme.INK);
        setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));

        JLabel title = new JLabel("LibGram", SwingConstants.CENTER);
        title.setForeground(Theme.WHITE);
        title.setFont(Theme.HEADING);
        add(title, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        JLabel identity = new JLabel(user.displayName() + "  |  " + user.role().name());
        identity.setForeground(Theme.WHITE);
        actions.add(identity);

        JButton settingsButton = new JButton("\u2699");
        settingsButton.setToolTipText(permissions.canOpenSettings(user) ? "Settings"
                : "Only an admin can change settings");
        settingsButton.getAccessibleContext().setAccessibleName("Settings");
        settingsButton.setEnabled(permissions.canOpenSettings(user));
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(event -> new SettingsDialog(owner, user, settings, accounts, alertScanner).setVisible(true));
        actions.add(settingsButton);

        JButton about = new JButton("About");
        about.setMnemonic('A');
        about.setToolTipText("About LibGram");
        about.addActionListener(event -> {
            String storage;
            try { storage = StoragePaths.storageDirectory().toString(); }
            catch (java.io.IOException exception) { storage = "Unavailable"; }
            javax.swing.JOptionPane.showMessageDialog(owner,
                    "LibGram\nVersion: Phase 9\nJava: " + System.getProperty("java.version")
                            + "\nStorage: " + storage,
                    "About LibGram", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        });
        actions.add(about);

        JButton logout = new JButton("Logout");
        logout.setMnemonic('L');
        logout.setFocusPainted(false);
        logout.addActionListener(event -> onLogout.run());
        actions.add(logout);
        add(actions, BorderLayout.EAST);
    }
}
