import data.CsvBookRepository;
import data.CsvContactRepository;
import data.CsvLoanRepository;
import data.CsvNotificationRepository;
import data.CsvSettingsRepository;
import data.CsvUserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import model.User;
import services.AlertScanner;
import services.AuthService;
import services.BookNotificationListener;
import services.BookService;
import services.ContactService;
import services.LendingService;
import services.LoanNotificationListener;
import services.NotificationService;
import services.SettingsService;
import ui.MainFrame;

public final class MainFrameInitializationTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path root = Files.createTempDirectory("libgrampocket-main-frame-");
        NotificationService notifications = null;
        MainFrame[] mainFrame = new MainFrame[1];
        try {
            Path account = root.resolve("users/reader");
            AuthService auth = new AuthService(new CsvUserRepository(root.resolve("users.txt")));
            User user = auth.signUp("reader", "Reader", "reader-password".toCharArray(),
                    "reader-password".toCharArray());
            CsvBookRepository books = new CsvBookRepository(account.resolve("books.txt"));
            CsvContactRepository contacts = new CsvContactRepository(account.resolve("contacts.txt"));
            CsvLoanRepository loans = new CsvLoanRepository(account.resolve("loans.txt"));
            SettingsService settings = new SettingsService(new CsvSettingsRepository(account.resolve("settings.txt")));
            notifications = new NotificationService(
                    new CsvNotificationRepository(account.resolve("notifications.txt")));
            ContactService contactService = new ContactService(contacts, loans);
            BookService bookService = new BookService(books, new BookNotificationListener(notifications), loans,
                    contacts);
            LendingService lending = new LendingService(books, loans, contacts, settings,
                    new LoanNotificationListener(notifications, contactService));
            AlertScanner scanner = new AlertScanner(lending, contactService, settings, notifications);
            NotificationService accountNotifications = notifications;
            SwingUtilities.invokeAndWait(() -> {
                try {
                    mainFrame[0] = new MainFrame(null, user, auth, root, bookService,
                            contactService, settings, lending, accountNotifications, scanner, () -> {
                            });
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });
            check(mainFrame[0] != null);
            System.out.println("MainFrameInitializationTest passed");
        } finally {
            if (mainFrame[0] != null) {
                SwingUtilities.invokeAndWait(mainFrame[0]::shutdown);
            }
            if (notifications != null) {
                notifications.close();
            }
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Main window initialization failed");
        }
    }
}