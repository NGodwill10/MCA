import data.AccountPaths;
import data.CsvUtil;
import data.CsvBookRepository;
import data.CsvContactRepository;
import data.CsvLoanRepository;
import data.CsvNotificationRepository;
import data.CsvSettingsRepository;
import data.CsvUserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import data.StorageLock;
import services.AuthService;
import services.AppLog;
import services.AlertScanner;
import services.BookService;
import services.BookNotificationListener;
import services.ContactService;
import services.LendingService;
import services.LoanNotificationListener;
import services.NotificationService;
import services.SettingsService;
import ui.AppWindow;
import ui.Theme;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                Theme.installNimbus();
                Path storage = data.StoragePaths.storageDirectory(args);
                Files.createDirectories(storage);
                StorageLock lock = StorageLock.tryAcquire(storage).orElse(null);
                if (lock == null) {
                    JOptionPane.showMessageDialog(null, "LibGramPocket is already running.",
                            "Already running", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    try {
                        lock.close();
                    } catch (IOException exception) {
                        System.getLogger(Main.class.getName()).log(System.Logger.Level.ERROR,
                                "Unable to release storage lock", exception);
                    }
                }, "libgrampocket-storage-lock-release"));
                AppLog.configure(storage);
                Path usersDirectory = storage.resolve("users");
                Path usersFile = storage.resolve("users.txt");
                Set<String> previousCorruptFiles = corruptFiles(storage);
                AuthService authService = new AuthService(new CsvUserRepository(usersFile));
                List<String> userRecovery = newCorruptFiles(storage, previousCorruptFiles);
                if (!userRecovery.isEmpty()) {
                    JOptionPane.showMessageDialog(null,
                            "The account list needed recovery: " + String.join(", ", userRecovery)
                                    + ". Readable backups were restored; unreadable files were retained with a "
                                    + ".corrupt timestamp suffix.",
                            "Account data recovered", JOptionPane.WARNING_MESSAGE);
                }
                AppWindow.AccountServicesFactory accountServicesFactory = user -> {
                    Path accountDirectory = AccountPaths.forUsername(usersDirectory, user.username());
                    Set<String> corruptFilesBeforeLoad = corruptFiles(accountDirectory);
                    CsvBookRepository books = new CsvBookRepository(accountDirectory.resolve("books.txt"));
                    CsvContactRepository contacts = new CsvContactRepository(accountDirectory.resolve("contacts.txt"));
                    CsvLoanRepository loans = new CsvLoanRepository(accountDirectory.resolve("loans.txt"));
                    SettingsService settings = new SettingsService(
                            new CsvSettingsRepository(accountDirectory.resolve("settings.txt")));
                    CsvNotificationRepository notificationRepository = new CsvNotificationRepository(
                            accountDirectory.resolve("notifications.txt"));
                    NotificationService notifications = new NotificationService(
                            notificationRepository);
                    CsvUtil.createDailyBackups(accountDirectory, LocalDate.now());
                    notifications.pruneDismissedBefore(notifications.now().minusDays(365));
                    ContactService contactService = new ContactService(contacts, loans);
                    BookService bookService = new BookService(books,
                            new BookNotificationListener(notifications), loans, contacts);
                    LendingService lending = new LendingService(books, loans, contacts, settings,
                            new LoanNotificationListener(notifications, contactService));
                    AlertScanner alertScanner = new AlertScanner(lending, contactService, settings, notifications);
                    return new AppWindow.AccountServices(bookService, contactService, settings, lending,
                            notifications, alertScanner, newCorruptFiles(accountDirectory, corruptFilesBeforeLoad));
                };
                new AppWindow(authService, accountServicesFactory, storage).show();
            } catch (IOException | IllegalArgumentException exception) {
                System.getLogger(Main.class.getName()).log(System.Logger.Level.ERROR,
                        "Unable to initialize LibGramPocket storage", exception);
                JOptionPane.showMessageDialog(null, exception instanceof IllegalArgumentException
                        ? exception.getMessage()
                        : "LibGramPocket could not open its local data files.",
                        "Startup error", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static Set<String> corruptFiles(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Set.of();
        }
        try (var paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().contains(".corrupt-"))
                    .map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet());
        }
    }

    private static List<String> newCorruptFiles(Path directory, Set<String> previous) throws IOException {
        Set<String> current = new HashSet<>(corruptFiles(directory));
        current.removeAll(previous);
        return current.stream().sorted().toList();
    }
}