import data.AccountPaths;
import data.CsvBookRepository;
import data.CsvContactRepository;
import data.CsvLoanRepository;
import data.CsvNotificationRepository;
import data.CsvSettingsRepository;
import data.CsvUserRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import services.AuthService;
import services.BookSearchField;
import services.BookService;
import services.ContactService;
import services.LendingService;
import services.SettingsService;

public final class PerformanceCheck {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("libgrampocket-performance-");
        try {
            Path storage = root.resolve("storage-demo");
            DemoData.generate(storage, true);
            measure(storage);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void measure(Path storage) throws Exception {
        long start = System.nanoTime();
        AuthService auth = new AuthService(new CsvUserRepository(storage.resolve("users.txt")));
        Optional<?> signedIn = auth.authenticate("demo", "demo-password".toCharArray());
        check(signedIn.isPresent(), "demo sign-in");
        Path account = AccountPaths.forUsername(storage.resolve("users"), "demo");
        CsvBookRepository books = new CsvBookRepository(account.resolve("books.txt"));
        CsvContactRepository contacts = new CsvContactRepository(account.resolve("contacts.txt"));
        CsvLoanRepository loans = new CsvLoanRepository(account.resolve("loans.txt"));
        SettingsService settings = new SettingsService(new CsvSettingsRepository(account.resolve("settings.txt")));
        new CsvNotificationRepository(account.resolve("notifications.txt"));
        LendingService lending = new LendingService(books, loans, contacts, settings);
        BookService bookService = new BookService(books, loans, contacts);
        ContactService contactService = new ContactService(contacts, loans);
        long loadMillis = elapsedMillis(start);

        start = System.nanoTime();
        check(bookService.search(BookSearchField.TITLE, "Example Volume 04999", false).size() == 1,
                "large catalog search");
        long searchMillis = elapsedMillis(start);

        start = System.nanoTime();
        check(!lending.activeBorrows().isEmpty(), "active borrow query");
        long activeMillis = elapsedMillis(start);

        start = System.nanoTime();
        check(!lending.loansByContact(contacts.findAll().getFirst().contactId()).isEmpty(), "contact detail query");
        long contactMillis = elapsedMillis(start);

        start = System.nanoTime();
        loans.saveAll(loans.findAll());
        long writeMillis = elapsedMillis(start);

        System.out.printf(java.util.Locale.ROOT,
                "Large dataset timings (ms): sign-in/load=%d, search=%d, active=%d, contact=%d, loan rewrite=%d%n",
                loadMillis, searchMillis, activeMillis, contactMillis, writeMillis);
        check(loadMillis <= 2_000, "sign-in and all account file loading under 2000 ms");
        check(searchMillis <= 100, "search under 100 ms");
        check(activeMillis <= 300, "active borrow query under 300 ms");
        check(contactMillis <= 300, "contact detail query under 300 ms");
        check(writeMillis <= 200, "single full loan-file write under 200 ms");
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError("Performance target not met: " + description);
        }
    }
}