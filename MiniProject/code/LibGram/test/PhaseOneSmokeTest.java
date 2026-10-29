import data.CsvBookRepository;
import data.CsvUtil;
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
        Path temporaryDirectory = Files.createTempDirectory("libgram-phase1-");
        try {
            verifyCsvQuoting(temporaryDirectory);
            verifyBookRepository(temporaryDirectory);
            verifyMalformedBookRows(temporaryDirectory);
            verifyAuthenticationPersistence(temporaryDirectory);
            verifyProportions();
            System.out.println("Phase 1 smoke tests passed.");
        } finally {
            deleteTree(temporaryDirectory);
        }
    }

    private static void verifyCsvQuoting(Path directory) throws Exception {
        Path file = directory.resolve("quoted.txt");
        List<String> expectedHeader = List.of("value");
        List<List<String>> expectedRows = List.of(List.of("Smith, \"Jane\"\nSecond line"));
        CsvUtil.writeAtomically(file, expectedHeader, expectedRows);
        List<List<String>> actual = CsvUtil.read(file);
        assertEquals(expectedHeader, actual.getFirst(), "CSV header round-trip");
        assertEquals(expectedRows.getFirst(), actual.get(1), "CSV quoted-field round-trip");
    }

    private static void verifyBookRepository(Path directory) throws Exception {
        Path file = directory.resolve("books.txt");
        CsvBookRepository repository = new CsvBookRepository(file);
        assertTrue(repository.findAll().isEmpty(), "New catalog starts empty");
        assertTrue(Files.readString(file).startsWith("bookId,isbn,title,authors"), "New catalog has a CSV header");
        Book expected = new Book("book-1", "978-1", "A, \"Great\" Book\nVolume 2", "Smith, John",
                "Press", "2026", "2nd", "Fiction", "English", "A-1", new BigDecimal("125.20"),
                3, 2, LocalDate.of(2026, 10, 20));
        repository.saveAll(List.of(expected));
        assertEquals(List.of(expected), repository.findAll(), "Book repository round-trip");
        repository.saveAll(List.of());
        assertTrue(Files.exists(file.resolveSibling("books.txt.bak")), "Repository backup created");
    }

    private static void verifyMalformedBookRows(Path directory) throws Exception {
        Path file = directory.resolve("malformed-books.txt");
        List<String> header = List.of("bookId", "isbn", "title", "authors", "publisher", "publicationYear",
                "edition", "category", "language", "shelfLocation", "price", "totalCopies",
                "availableCopies", "dateAdded");
        List<List<String>> rows = List.of(
                List.of("bad-date", "", "Malformed", "Author", "", "", "", "", "", "", "1.00", "1", "1",
                        "not-a-date"),
                List.of("valid", "", "Valid Book", "Author", "", "", "", "", "", "", "1.00", "1", "1",
                        "2026-10-20"));
        CsvUtil.writeAtomically(file, header, rows);
        List<Book> books = new CsvBookRepository(file).findAll();
        assertEquals(1, books.size(), "A malformed date row is skipped without aborting the catalog load");
        assertEquals("Valid Book", books.getFirst().title(), "Valid row after malformed date still loads");
    }

    private static void verifyAuthenticationPersistence(Path directory) throws Exception {
        Path file = directory.resolve("users.txt");
        AuthService firstLaunch = new AuthService(new CsvUserRepository(file));
        char[] password = "local-test-password".toCharArray();
        User admin = firstLaunch.createInitialAdmin(" librarian ", "Library Admin", password);
        assertEquals("librarian", admin.username(), "Username trimming");
        assertTrue(!Files.readString(file).contains("local-test-password"), "Password is not stored in plain text");

        AuthService nextLaunch = new AuthService(new CsvUserRepository(file));
        assertTrue(nextLaunch.hasAccounts(), "Account persists across repository restart");
        assertTrue(nextLaunch.authenticate("LIBRARIAN", "local-test-password".toCharArray()).isPresent(),
                "Correct password authenticates");
        assertTrue(nextLaunch.authenticate("librarian", "incorrect".toCharArray()).isEmpty(),
                "Incorrect password is rejected");
        nextLaunch.authenticate("librarian", "incorrect".toCharArray());
        nextLaunch.authenticate("librarian", "incorrect".toCharArray());
        assertTrue(nextLaunch.lockoutMillisRemaining() > 0, "Three consecutive failures trigger a lockout");
    }

    private static void verifyProportions() {
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
            assertEquals((int) Math.round(viewport[1] * 0.10), title.getHeight(), "Title is ten percent of height");
            assertEquals(viewport[1], title.getHeight() + body.getHeight(), "Vertical regions fill viewport");
            assertEquals((int) Math.round(viewport[0] * 0.25), body.getComponents()[0].getWidth(),
                    "Left panel is one quarter of width");
            assertEquals((int) Math.round(viewport[0] * 0.75),
                    body.getComponents()[0].getWidth() + body.getComponents()[1].getWidth(),
                    "Center panel ends at three quarters of width");
            assertEquals(viewport[0], body.getComponents()[0].getWidth()
                    + body.getComponents()[1].getWidth() + body.getComponents()[2].getWidth(),
                    "Horizontal regions fill viewport");
            assertEquals(0, title.getY(), "Title starts at top edge");
            assertEquals(title.getHeight(), body.getY(), "Body follows title");
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

    private static void deleteTree(Path directory) throws Exception {
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}