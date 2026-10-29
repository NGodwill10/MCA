import data.CsvBookRepository;
import data.CsvUtil;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import model.Book;
import model.Book.Condition;
import model.Book.OwnershipType;
import model.Book.ReadingStatus;
import services.BookChangeListener;
import services.BookSearchField;
import services.BookService;
import services.DuplicateIsbnException;

public final class BookServiceTest {
    private BookServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path temporaryDirectory = Files.createTempDirectory("libgram-pocket-books-phase2-");
        try {
            verifyRulesAndSearch(temporaryDirectory);
            verifyAccountIsolation(temporaryDirectory);
            verifyNotesCsvRoundTrip(temporaryDirectory);
            System.out.println("Pocket BookService tests passed.");
        } finally {
            try (var paths = Files.walk(temporaryDirectory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyRulesAndSearch(Path directory) throws Exception {
        CsvBookRepository repository = new CsvBookRepository(directory.resolve("owner-a.txt"));
        List<String> changes = new ArrayList<>();
        BookService service = new BookService(repository, (change, book) -> changes.add(change.name()));

        Book hobbit = service.add(draft(" 978-0-618-26030-0 ", "The Hobbit", "J.R.R. Tolkien",
                "HarperCollins", "1937", "Fantasy", "English", BigDecimal.ZERO, 3, 3,
                Condition.GOOD, ReadingStatus.UNREAD, "first edition"));
        assertTrue(!hobbit.bookId().isBlank(), "ID is generated");
        assertEquals(LocalDate.now(), hobbit.dateAdded(), "Date is assigned automatically");
        assertEquals(OwnershipType.OWNED, hobbit.ownershipType(), "Added books are owned");
        assertEquals("", hobbit.lenderContactId(), "Owned book has no lender");
        assertEquals("9780618260300", hobbit.isbn(), "ISBN spaces and hyphens are stripped");
        assertEquals(hobbit, service.getById(hobbit.bookId()).orElseThrow(), "Get by ID returns the account record");
        assertEquals(3, hobbit.availableCopies(), "All new copies are available");
        assertEquals(List.of("ADDED"), changes, "Add change hook fires");

        Book unread = service.add(draft("", "A Wizard of Earthsea", "Ursula K. Le Guin", "Parnassus", "1968",
                "Fantasy", "English", null, 2, 2, null, null, "notes, with \"quotes\"\nand a second line"));
        assertEquals(BigDecimal.ZERO.setScale(2), unread.price(), "Missing price defaults to zero");
        assertEquals(Condition.GOOD, unread.condition(), "Condition defaults to Good");
        assertEquals(ReadingStatus.UNREAD, unread.readingStatus(), "Reading status defaults to Unread");
        Book reading = service.update(new Book(unread.bookId(), OwnershipType.OWNED, "", unread.isbn(), unread.title(),
                unread.authors(), unread.publisher(), unread.publicationYear(), unread.edition(), unread.category(),
                unread.language(), unread.price(), 2, 1, unread.condition(), ReadingStatus.READING, unread.notes(),
                unread.dateAdded(), false));
        Book lent = withAvailableCopies(reading, 1);
        repository.saveAll(repository.findAll().stream()
                .map(book -> book.bookId().equals(lent.bookId()) ? lent : book).toList());

        assertEquals("The Hobbit", service.search(BookSearchField.TITLE, "hobbit", false).getFirst().title(),
                "Title filter is case-insensitive contains");
        assertEquals(1, service.search(BookSearchField.AUTHOR, "tolkien", true).size(),
                "Author filter combines with availability");
        assertEquals(1, service.search(BookSearchField.ISBN, "978-0-618", false).size(), "ISBN filter matches");
        assertEquals(1, service.search(BookSearchField.PUBLISHER, "harper", false).size(), "Publisher filter matches");
        assertEquals(2, service.search(BookSearchField.CATEGORY, "fant", false).size(), "Category filter matches");
        assertEquals(2, service.search(BookSearchField.LANGUAGE, "english", false).size(), "Language filter matches");
        assertEquals(1, service.search(BookSearchField.YEAR, "193", false).size(), "Year filter matches");
        assertEquals(1, service.search(BookSearchField.READING_STATUS, "reading", false).size(),
                "Reading status filter matches");
        assertEquals("A Wizard of Earthsea", service.listSorted().getFirst().title(), "Default sort is title A-Z");
        assertTrue(service.search(BookSearchField.ALL, "tolkien", false).contains(hobbit),
                "All-field search covers searchable text");

        expectIllegalArgument(() -> service.add(draft("", "", "Author", "", "", "", "", BigDecimal.ZERO,
                1, 1, null, null, "")), "Title is required");
        expectIllegalArgument(() -> service.add(draft("", "No author", "  ", "", "", "", "", BigDecimal.ZERO,
                1, 1, null, null, "")), "Author is required");
        expectIllegalArgument(() -> service.add(draft("", "No copies", "Author", "", "", "", "", BigDecimal.ZERO,
                0, 0, null, null, "")), "At least one copy is required");
        expectIllegalArgument(() -> service.add(draft("", "Too expensive", "Author", "", "", "", "",
                new BigDecimal("-0.01"), 1, 1, null, null, "")), "Negative price is rejected");
        expectIllegalArgument(() -> service.add(draft("123", "Bad ISBN", "Author", "", "", "", "",
                BigDecimal.ZERO, 1, 1, null, null, "")), "Malformed ISBN is rejected");
        expectIllegalArgument(() -> service.add(draft("", "Bad year", "Author", "", "999", "", "",
                BigDecimal.ZERO, 1, 1, null, null, "")), "Out-of-range year is rejected");
        expectIllegalArgument(() -> service.add(draft("", "Bad year text", "Author", "", "19xx", "", "",
                BigDecimal.ZERO, 1, 1, null, null, "")), "Non-numeric year is rejected");
        expectIllegalArgument(() -> service.add(draft("", "T".repeat(201), "Author", "", "", "", "",
                BigDecimal.ZERO, 1, 1, null, null, "")), "Titles longer than 200 characters are rejected");

        try {
            service.add(draft("9780618260300", "Duplicate ISBN", "Author", "", "", "", "", BigDecimal.ZERO,
                    1, 1, null, null, ""));
            throw new AssertionError("Duplicate ISBN must fail");
        } catch (DuplicateIsbnException exception) {
            assertEquals(hobbit.bookId(), exception.existingBook().bookId(), "Duplicate exposes existing book");
        }
        service.add(draft("", "No ISBN 1", "Author", "", "", "", "", BigDecimal.ZERO, 1, 1, null, null, ""));
        service.add(draft("", "No ISBN 2", "Author", "", "", "", "", BigDecimal.ZERO, 1, 1, null, null, ""));

        expectIllegalArgument(() -> service.update(new Book(lent.bookId(), OwnershipType.OWNED, "", lent.isbn(),
                lent.title(), lent.authors(), lent.publisher(), lent.publicationYear(), lent.edition(),
                lent.category(), lent.language(), lent.price(), 0, 0, lent.condition(), lent.readingStatus(),
                lent.notes(), lent.dateAdded(), false)), "Owned copies cannot go below copies out");
        Book updated = service.update(new Book(lent.bookId(), OwnershipType.OWNED, "", lent.isbn(), lent.title(),
                lent.authors(), lent.publisher(), lent.publicationYear(), lent.edition(), lent.category(),
                lent.language(), lent.price(), 3, 0, Condition.WORN, ReadingStatus.READ, "updated", LocalDate.MIN,
                false));
        assertEquals(lent.bookId(), updated.bookId(), "Update preserves ID");
        assertEquals(unread.dateAdded(), updated.dateAdded(), "Update preserves date added");
        assertEquals(2, updated.availableCopies(), "Update preserves copies out");
        assertEquals(List.of("ADDED", "ADDED", "UPDATED", "ADDED", "ADDED", "UPDATED"), changes,
                "Add and update change hooks fire");

        expectIllegalArgument(() -> service.remove(lent.bookId()), "Removal is blocked while a copy is out");
        List<Book> returned = repository.findAll().stream()
                .map(book -> book.bookId().equals(updated.bookId()) ? withAvailableCopies(book, book.totalCopies())
                        : book)
                .toList();
        repository.saveAll(returned);
        service.remove(updated.bookId());
        assertTrue(changes.getLast().equals("REMOVED"), "Remove change hook fires");
        Book isbn10 = service.add(draft("0-306-40615-x", "ISBN Ten", "Author", "", "", "", "",
                BigDecimal.ZERO, 1, 1, null, null, ""));
        assertEquals("030640615X", isbn10.isbn(), "ISBN-10 final X is accepted and normalized");
    }

    private static void verifyAccountIsolation(Path directory) throws Exception {
        BookService accountA = new BookService(new CsvBookRepository(directory.resolve("account-a-books.txt")));
        BookService accountB = new BookService(new CsvBookRepository(directory.resolve("account-b-books.txt")));
        accountA.add(draft("", "Private book", "Author", "", "", "", "", BigDecimal.ZERO,
                1, 1, null, null, ""));
        assertTrue(accountB.listSorted().isEmpty(), "Account B cannot see account A's catalog");
    }

    private static void verifyNotesCsvRoundTrip(Path directory) throws Exception {
        Path file = directory.resolve("notes-books.txt");
        BookService service = new BookService(new CsvBookRepository(file));
        String notes = "Borrowed from, \"Sam\"\nReturn next week";
        service.add(draft("", "Quoted notes", "Author", "", "", "", "", BigDecimal.ZERO,
                1, 1, null, null, notes));
        CsvBookRepository restarted = new CsvBookRepository(file);
        assertEquals(notes, restarted.findAll().getFirst().notes(),
                "CSV preserves commas, quotes, and newlines in notes");
        assertTrue(CsvUtil.read(file).size() == 2, "CSV file has one header and one book row");
    }

    private static Book draft(String isbn, String title, String authors, String publisher, String year, String category,
            String language, BigDecimal price, int total, int available, Condition condition,
            ReadingStatus readingStatus, String notes) {
        return new Book("", OwnershipType.BORROWED, "not-used", isbn, title, authors, publisher, year, "1st",
                category, language, price, total, available, condition, readingStatus, notes, LocalDate.now(), true);
    }

    private static Book withAvailableCopies(Book book, int availableCopies) {
        return new Book(book.bookId(), book.ownershipType(), book.lenderContactId(), book.isbn(), book.title(),
                book.authors(), book.publisher(), book.publicationYear(), book.edition(), book.category(),
                book.language(), book.price(), book.totalCopies(), availableCopies, book.condition(),
                book.readingStatus(), book.notes(), book.dateAdded(), book.archived());
    }

    private static void expectIllegalArgument(ThrowingAction action, String description) throws Exception {
        try {
            action.run();
            throw new AssertionError(description);
        } catch (IllegalArgumentException expected) {
            // Expected validation failure.
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
