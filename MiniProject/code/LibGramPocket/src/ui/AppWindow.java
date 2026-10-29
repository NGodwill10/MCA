package ui;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import model.User;
import services.AuthService;
import services.BookService;
import services.ContactService;
import services.LendingService;
import services.AlertScanner;
import services.NotificationService;
import services.SettingsService;

public final class AppWindow {
    @FunctionalInterface
    public interface AccountServicesFactory {
        AccountServices create(User user) throws IOException;
    }

    public record AccountServices(BookService bookService, ContactService contactService,
            SettingsService settingsService, LendingService lendingService,
            NotificationService notificationService, AlertScanner alertScanner, List<String> recoveredFiles) {
    }

    private final JFrame frame = new JFrame("LibGramPocket");
    private final AuthService authService;
    private final AccountServicesFactory accountServicesFactory;
    private final Path storageDirectory;
    private MainFrame mainPanel;

    public AppWindow(AuthService authService, AccountServicesFactory accountServicesFactory, Path storageDirectory) {
        this.authService = authService;
        this.accountServicesFactory = accountServicesFactory;
        this.storageDirectory = storageDirectory;
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(980, 620));
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (mainPanel != null) {
                    mainPanel.shutdown();
                }
            }
        });
        showLogin();
    }

    public void show() {
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }

    private void showLogin() {
        frame.setContentPane(new LoginPanel(authService, this::showMain));
        frame.revalidate();
        frame.repaint();
    }

    private void showMain(User user) {
        new SwingWorker<AccountServices, Void>() {
            @Override
            protected AccountServices doInBackground() throws Exception {
                return accountServicesFactory.create(user);
            }

            @Override
            protected void done() {
                try {
                    AccountServices account = get();
                    mainPanel = new MainFrame(frame, user, authService, storageDirectory,
                            account.bookService(), account.contactService(),
                            account.settingsService(), account.lendingService(), account.notificationService(),
                            account.alertScanner(), AppWindow.this::logout);
                    frame.setContentPane(mainPanel);
                    frame.revalidate();
                    frame.repaint();
                    if (account.lendingService().correctedBookCount() > 0) {
                        JOptionPane.showMessageDialog(frame,
                                "Counts for " + account.lendingService().correctedBookCount()
                                        + " books were corrected.",
                                "Catalog repaired", JOptionPane.INFORMATION_MESSAGE);
                    }
                    if (!account.recoveredFiles().isEmpty()) {
                        JOptionPane.showMessageDialog(frame,
                                "These account files needed recovery: " + String.join(", ", account.recoveredFiles())
                                        + ". Readable backups were restored. Unreadable files were retained with a "
                                        + ".corrupt timestamp suffix.",
                                "Account data recovered", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException exception) {
                    System.getLogger(AppWindow.class.getName()).log(System.Logger.Level.ERROR,
                            "Unable to load account data", exception.getCause());
                    JOptionPane.showMessageDialog(frame, "Your account data could not be loaded.",
                            "Data error", JOptionPane.ERROR_MESSAGE);
                } catch (IOException exception) {
                    System.getLogger(AppWindow.class.getName()).log(System.Logger.Level.ERROR,
                            "Unable to initialize account window", exception);
                    JOptionPane.showMessageDialog(frame, "Your account data could not be loaded.",
                            "Data error", JOptionPane.ERROR_MESSAGE);
                } catch (RuntimeException exception) {
                    System.getLogger(AppWindow.class.getName()).log(System.Logger.Level.ERROR,
                            "Unable to construct the account window", exception);
                    JOptionPane.showMessageDialog(frame,
                            "Your account loaded, but its main screen could not be opened. Try signing in again.",
                            "Window error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void logout() {
        authService.logout();
        mainPanel = null;
        showLogin();
    }
}