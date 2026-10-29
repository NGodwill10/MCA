import data.CsvBookRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import model.Book;
import model.User;
import model.User.Role;
import services.CatalogService;
import services.DuplicateIsbnException;

public final class CatalogServiceTest {
    private CatalogServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("libgram-catalog-phase2-");
        try {
            verifyCatalogOperations(temporaryDirectory.resolve("books.txt"));
            System.out.println("Catalog service tests passed.");
        } finally {
            try (var paths = Files.walk(temporaryDirectory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyCatalogOperations(Path catalogFile) throws Exception {
        CsvBookRepository repository = new CsvBookRepository(catalogFile);
        CatalogService catalog = new CatalogService(repository);
        User admin = user(Role.ADMIN, true);
        User staff = user(Role.STAFF, true);
        Book created = catalog.addBook(admin, draft("  123456789X ", "  The Title ", "  Ada Lovelace ",
                new BigDecimal("12.345"), 3, 0));
        assertTrue(!created.bookId().isBlank(), "Book ID generated");
        assertEquals("The Title", created.title(), "Title trimmed");
        assertEquals("Ada Lovelace", created.authors(), "Author trimmed");
        assertEquals(new BigDecimal("12.35"), created.price(), "Price rounded to two decimals");
        assertEquals(3, created.availableCopies(), "New book starts fully available");
        assertTrue(created.dateAdded().equals(LocalDate.now()), "Date added assigned at creation");

        try {
            catalog.addBook(admin, draft("123 456789X", "Duplicate", "Author", BigDecimal.ONE, 1, 1));
            throw new AssertionError("Duplicate ISBN must be rejected");
        } catch (DuplicateIsbnException exception) {
            assertEquals(created.bookId(), exception.existingBook().bookId(), "Duplicate points to existing record");
        }

        Book lentCopy = new Book(created.bookId(), created.isbn(), created.title(), created.authors(),
                created.publisher(), created.publicationYear(), created.edition(), created.category(),
                created.language(), created.shelfLocation(), created.price(), 3, 2, created.dateAdded());
        repository.saveAll(List.of(lentCopy));
        Book updated = catalog.updateBook(admin, new Book(created.bookId(), "123456789X", "Updated title", "Author",
                "Publisher", "2025", "2nd", "History", "English", "B-2", new BigDecimal("15.00"),
                4, 0, LocalDate.of(2000, 1, 1)));
        assertEquals(created.bookId(), updated.bookId(), "Update preserves ID");
        assertEquals(created.dateAdded(), updated.dateAdded(), "Update preserves date added");
        assertEquals(3, updated.availableCopies(), "Update preserves copies currently lent out");

        expectIllegalArgument(() -> catalog.updateBook(admin, new Book(created.bookId(), "123456789X", "Too few",
                "Author", "", "", "", "", "", "", BigDecimal.ONE, 0, 0, created.dateAdded())),
                "Total copies cannot be zero");
        expectIllegalArgument(() -> catalog.removeBook(admin, created.bookId()), "Lent books cannot be removed");
        expectSecurity(() -> catalog.addBook(staff, draft("", "Staff book", "Author", BigDecimal.ONE, 1, 1)),
                "Staff cannot add books");
        expectSecurity(() -> catalog.addBook(user(Role.ADMIN, false),
                draft("", "Disabled book", "Author", BigDecimal.ONE, 1, 1)), "Inactive admins cannot add books");

        Book returnedCopy = new Book(updated.bookId(), updated.isbn(), updated.title(), updated.authors(),
                updated.publisher(), updated.publicationYear(), updated.edition(), updated.category(),
                updated.language(), updated.shelfLocation(), updated.price(), updated.totalCopies(),
                updated.totalCopies(), updated.dateAdded());
        repository.saveAll(List.of(returnedCopy));
        catalog.removeBook(admin, updated.bookId());
        assertTrue(catalog.loadBooks().isEmpty(), "Book removed from catalog");
    }

    private static Book draft(String isbn, String title, String authors, BigDecimal price, int total, int available) {
        return new Book("", isbn, title, authors, "Publisher", "2025", "1st", "Fiction", "English", "A-1",
                price, total, available, LocalDate.now());
    }

    private static User user(Role role, boolean active) {
        return new User("user-1", "librarian", "Library Admin", role, "salt", "hash", active,
                LocalDateTime.now());
    }

    private static void expectIllegalArgument(ThrowingAction action, String description) throws Exception {
        try {
            action.run();
            throw new AssertionError(description);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
        }
    }

    private static void expectSecurity(ThrowingAction action, String description) throws Exception {
        try {
            action.run();
            throw new AssertionError(description);
        } catch (SecurityException expected) {
            // Expected permission failure.
        }
    }

    private static void assertEquals(Object expected, Object actual, String description) {
        if (!expected.equals(actual)) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String description) {
        if (expected != actual) {
            throw new AssertionError(description + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String description) {
        if (!condition) {
            throw new AssertionError(description);
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
