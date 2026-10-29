import data.AccountPaths;
import data.CsvBookRepository;
import data.CsvUserRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import javax.swing.JPanel;
import model.Book;
import model.User;
import services.AuthService;
import ui.ProportionalLayout;

public final class PhaseOneSmokeTest {
    private PhaseOneSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("libgram-pocket-phase1-");
        try {
            verifyAccountIsolation(temporaryDirectory);
            verifyLayout();
            System.out.println("LibGramPocket Phase 1 smoke tests passed.");
        } finally {
            deleteTree(temporaryDirectory);
        }
    }

    private static void verifyAccountIsolation(Path directory) throws Exception {
        Path usersDirectory = directory.resolve("users");
        Path accountsFile = directory.resolve("users.txt");
        AuthService authService = new AuthService(new CsvUserRepository(accountsFile));
        User alice = authService.signUp("Alice", "Alice Reader", "alice-test-password".toCharArray(),
                "alice-test-password".toCharArray());
        User bob = authService.signUp("Bob", "", "bob-test-password".toCharArray(),
                "bob-test-password".toCharArray());
        assertEquals("Bob", bob.displayName(), "Optional display name defaults to username");
        assertTrue(!Files.readString(accountsFile).contains("alice-test-password"),
                "Passwords are not stored in plain text");
        assertThrows(() -> AccountPaths.forUsername(usersDirectory, "../Alice"), "Unsafe account path rejected");
        assertThrows(() -> authService.signUp("ALICE", "Duplicate", "another-password".toCharArray(),
                "another-password".toCharArray()), "Duplicate username rejected case-insensitively");
        assertThrows(() -> authService.signUp("charlie", "Charlie", "one".toCharArray(),
                "two".toCharArray()), "Password confirmation is required");

        Path aliceDirectory = AccountPaths.forUsername(usersDirectory, alice.username());
        Path bobDirectory = AccountPaths.forUsername(usersDirectory, bob.username());
        assertTrue(!aliceDirectory.equals(bobDirectory), "Accounts map to separate directories");
        Path emptyCatalogFile = directory.resolve("preexisting-empty-books.txt");
        Files.createFile(emptyCatalogFile);
        new CsvBookRepository(emptyCatalogFile);
        assertTrue(Files.readString(emptyCatalogFile).startsWith("bookId,ownershipType,lenderContactId"),
                "Existing empty catalog receives the schema header");
        CsvBookRepository aliceCatalog = new CsvBookRepository(aliceDirectory.resolve("books.txt"));
        CsvBookRepository bobCatalog = new CsvBookRepository(bobDirectory.resolve("books.txt"));
        Book book = new Book("book-1", Book.OwnershipType.BORROWED, "contact-1", "978-1",
                "A, \"Good\" Book\nVolume Two", "Smith, Jane", "Press", "2026", "2nd", "Fiction",
                "English", new BigDecimal("125.20"), 1, 1, Book.Condition.GOOD,
                Book.ReadingStatus.READING, "A multi-line\nnote", LocalDate.of(2026, 10, 20), false);
        aliceCatalog.saveAll(List.of(book));
        assertEquals(List.of(book), aliceCatalog.findAll(), "Full book schema survives CSV round-trip");
        assertTrue(bobCatalog.findAll().isEmpty(), "Other account cannot read Alice's catalog");
        assertTrue(Files.readString(aliceDirectory.resolve("books.txt")).contains("Borrowed"),
                "Book enum values use the schema labels");

        AuthService restarted = new AuthService(new CsvUserRepository(accountsFile));
        assertTrue(restarted.authenticate("ALICE", "alice-test-password".toCharArray()).isPresent(),
                "Account survives restart and username matching ignores case");
        assertTrue(restarted.authenticate("Alice", "wrong-password".toCharArray()).isEmpty(),
                "Wrong password is rejected");
        restarted.authenticate("Alice", "wrong-password".toCharArray());
        restarted.authenticate("Alice", "wrong-password".toCharArray());
        assertTrue(restarted.lockoutMillisRemaining() > 0, "Three failed logins trigger a short lockout");
        restarted.logout();
        assertTrue(restarted.currentUser().isEmpty(), "Logout clears the active account");
    }

    private static void verifyLayout() {
        for (int[] viewport : List.of(new int[] { 1366, 768 }, new int[] { 1920, 1080 }, new int[] { 1273, 811 })) {
            JPanel root = new JPanel(new ProportionalLayout(false, 0.10, 0.90));
            JPanel title = new JPanel();
            JPanel body = new JPanel(new ProportionalLayout(true, 0.25, 0.50, 0.25));
            body.add(new JPanel());
            body.add(new JPanel());
            body.add(new JPanel());
            root.add(title);
            root.add(body);
            root.setSize(viewport[0], viewport[1]);
            root.doLayout();
            body.doLayout();
            assertEquals((int) Math.round(viewport[1] * 0.10), title.getHeight(), "Title height is ten percent");
            assertEquals((int) Math.round(viewport[0] * 0.25), body.getComponents()[0].getWidth(),
                    "Left panel is one quarter width");
            assertEquals((int) Math.round(viewport[0] * 0.75),
                    body.getComponents()[0].getWidth() + body.getComponents()[1].getWidth(),
                    "Center panel ends at three quarters width");
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    private static void assertThrows(ThrowingAction action, String description) throws Exception {
        try {
            action.run();
            throw new AssertionError(description);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static void deleteTree(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}