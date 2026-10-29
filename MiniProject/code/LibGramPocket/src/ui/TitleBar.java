package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import model.User;
import services.AppInfo;
import services.AuthService;
import services.SettingsService;

public final class TitleBar extends JPanel {
    private static final long serialVersionUID = 1L;

    public TitleBar(Window owner, User user, SettingsService settingsService, AuthService authService,
            Path storageDirectory, Runnable onLogout) {
        setLayout(new BorderLayout());
        setBackground(Theme.INK);
        setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        JLabel title = new JLabel("LibGramPocket", SwingConstants.CENTER);
        title.setForeground(Theme.WHITE);
        title.setFont(Theme.HEADING);
        add(title, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        JButton userMenuButton = new JButton(user.username());
        userMenuButton.setToolTipText("Account options");
        JPopupMenu userMenu = new JPopupMenu();
        JMenuItem changePassword = new JMenuItem("Change password");
        changePassword.addActionListener(event -> new ChangePasswordDialog(owner, authService).setVisible(true));
        JMenuItem logout = new JMenuItem("Logout");
        logout.addActionListener(event -> onLogout.run());
        userMenu.add(changePassword);
        userMenu.addSeparator();
        userMenu.add(logout);
        userMenuButton.addActionListener(event -> userMenu.show(userMenuButton, 0, userMenuButton.getHeight()));
        actions.add(userMenuButton);
        JButton settings = new JButton("\u2699");
        settings.setToolTipText("Settings");
        settings.getAccessibleContext().setAccessibleName("Settings");
        settings.setFocusPainted(false);
        settings.addActionListener(event -> new SettingsDialog(owner, settingsService).setVisible(true));
        actions.add(settings);
        JMenuBar helpBar = new JMenuBar();
        JMenu help = new JMenu("Help");
        help.setMnemonic('H');
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(event -> JOptionPane.showMessageDialog(owner,
                "LibGramPocket " + AppInfo.VERSION + "\nJava " + System.getProperty("java.version")
                        + "\nStorage: " + storageDirectory.toAbsolutePath().normalize(),
                "About LibGramPocket", JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        helpBar.add(help);
        actions.add(helpBar);
        add(actions, BorderLayout.EAST);
    }
}