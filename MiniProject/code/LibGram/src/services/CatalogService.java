package services;

import data.BookRepository;
import data.CsvBookRepository;
import data.LoanRepository;
import data.ReservationRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import data.StoragePaths;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.Locale;
import model.Book;
import model.User;

public class CatalogService {
    private static final int MONEY_SCALE = 2;

    private final BookRepository store;
    private final PermissionPolicy permissions = new PermissionPolicy();
    private final BookChangeListener changeListener;
    private final LoanRepository loans;
    private final ReservationRepository reservations;

    public CatalogService() throws IOException {
        this(new CsvBookRepository(StoragePaths.storageDirectory().resolve("books.txt")));
    }

    public CatalogService(BookRepository store) {
        this(store, BookChangeListener.NO_OP);
    }

    public CatalogService(BookRepository store, BookChangeListener changeListener) {
        this(store, null, changeListener);
    }

    public CatalogService(BookRepository store, LoanRepository loans) {
        this(store, loans, BookChangeListener.NO_OP);
    }

    public CatalogService(BookRepository store, LoanRepository loans, BookChangeListener changeListener) {
        this(store, loans, null, changeListener);
    }

    public CatalogService(BookRepository store, LoanRepository loans, ReservationRepository reservations) {
        this(store, loans, reservations, BookChangeListener.NO_OP);
    }

    public CatalogService(BookRepository store, LoanRepository loans, ReservationRepository reservations,
            BookChangeListener changeListener) {
        this(store, loans, reservations, changeListener, true);
    }

    private CatalogService(BookRepository store, LoanRepository loans, ReservationRepository reservations,
            BookChangeListener changeListener, boolean ignored) {
        this.store = store;
        this.loans = loans;
        this.reservations = reservations;
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
    }

    public List<Book> loadBooks() throws IOException {
        return store.findAll();
    }

    /**
     * Adds a new catalog book after enforcing administrator permissions and catalog
     * invariants.
     *
     * @param actor the signed-in user performing the change
     * @param draft the values entered for the new book
     * @return the stored book with its generated ID and initial copy counts
     * @throws IOException              when the catalog cannot be saved
     * @throws IllegalArgumentException when required values are invalid
     * @throws SecurityException        when the actor cannot manage the catalog
     */
    public Book addBook(User actor, Book draft) throws IOException {
        requireCatalogPermission(actor);
        Book normalized = normalizeAndValidate(draft);
        ensureUniqueIsbn(normalized.isbn(), null, store.findAll());
        Book created = new Book(UUID.randomUUID().toString(), normalized.isbn(), normalized.title(),
                normalized.authors(), normalized.publisher(), normalized.publicationYear(), normalized.edition(),
                normalized.category(), normalized.language(), normalized.shelfLocation(), normalized.price(),
                normalized.totalCopies(), normalized.totalCopies(), LocalDate.now());
        List<Book> books = new java.util.ArrayList<>(store.findAll());
        books.add(created);
        store.saveAll(books);
        changeListener.onBookChanged(BookChangeListener.Change.ADDED, created);
        return created;
    }

    /**
     * Updates editable catalog fields while preserving the book ID, date added, and
     * active loan count.
     *
     * @param actor the signed-in user performing the change
     * @param draft the edited values, including the existing book ID
     * @return the updated book
     * @throws IOException              when the catalog cannot be saved
     * @throws IllegalArgumentException when the book or edited values are invalid
     * @throws SecurityException        when the actor cannot manage the catalog
     */
    public Book updateBook(User actor, Book draft) throws IOException {
        requireCatalogPermission(actor);
        Book normalized = normalizeAndValidate(draft);
        List<Book> books = new java.util.ArrayList<>(store.findAll());
        int index = findIndex(books, draft.bookId());
        if (index < 0) {
            throw new IllegalArgumentException("Select a book that still exists in the catalog.");
        }
        Book existing = books.get(index);
        ensureUniqueIsbn(normalized.isbn(), existing.bookId(), books);
        int lentCopies = activeLoanCount(existing.bookId());
        lentCopies = Math.max(lentCopies, existing.totalCopies() - existing.availableCopies());
        if (normalized.totalCopies() < lentCopies) {
            throw new IllegalArgumentException("Total copies cannot be less than the " + lentCopies
                    + " copies currently lent out.");
        }
        Book updated = new Book(existing.bookId(), normalized.isbn(), normalized.title(), normalized.authors(),
                normalized.publisher(), normalized.publicationYear(), normalized.edition(), normalized.category(),
                normalized.language(), normalized.shelfLocation(), normalized.price(), normalized.totalCopies(),
                normalized.totalCopies() - lentCopies, existing.dateAdded());
        books.set(index, updated);
        store.saveAll(books);
        changeListener.onBookChanged(BookChangeListener.Change.UPDATED, updated);
        return updated;
    }

    /**
     * Removes a catalog book that has no copies currently lent out.
     *
     * @param actor  the signed-in user performing the change
     * @param bookId the ID of the selected book
     * @return the removed book
     * @throws IOException              when the catalog cannot be saved
     * @throws IllegalArgumentException when the book is missing or still has lent
     *                                  copies
     * @throws SecurityException        when the actor cannot manage the catalog
     */
    public Book removeBook(User actor, String bookId) throws IOException {
        requireCatalogPermission(actor);
        List<Book> books = new java.util.ArrayList<>(store.findAll());
        int index = findIndex(books, bookId);
        if (index < 0) {
            throw new IllegalArgumentException("Select a book that still exists in the catalog.");
        }
        Book removed = books.get(index);
        int lentCopies = activeLoanCount(removed.bookId());
        lentCopies = Math.max(lentCopies, removed.totalCopies() - removed.availableCopies());
        if (lentCopies > 0) {
            throw new IllegalArgumentException("Cannot remove this book while " + lentCopies
                    + " copy/copies are currently lent out.");
        }
        if (reservations != null) {
            List<String> open = reservations.findAll().stream().filter(r -> r.bookId().equals(bookId))
                    .filter(r -> r.status() == model.ReservationStatus.PENDING || r.status() == model.ReservationStatus.READY)
                    .map(r -> r.memberId()).toList();
            if (!open.isEmpty()) throw new IllegalArgumentException("Cannot remove this book while reservations exist: " + open);
        }
        books.remove(index);
        store.saveAll(books);
        changeListener.onBookChanged(BookChangeListener.Change.REMOVED, removed);
        return removed;
    }

    private int activeLoanCount(String bookId) {
        if (loans == null) return 0;
        return (int) loans.findAll().stream().filter(loan -> loan.bookId().equals(bookId)
                && loan.status() == model.LoanStatus.ACTIVE).count();
    }

    private void requireCatalogPermission(User actor) {
        Objects.requireNonNull(actor, "actor");
        if (!actor.active() || !permissions.canManageCatalog(actor)) {
            throw new SecurityException("Only an active administrator can change the book catalog.");
        }
    }

    private static Book normalizeAndValidate(Book draft) {
        Objects.requireNonNull(draft, "book");
        String title = clean(draft.title());
        String authors = clean(draft.authors());
        if (title.isEmpty() || title.length() > 200) {
            throw new IllegalArgumentException(title.isEmpty() ? "Title is required." : "Title must be 200 characters or fewer.");
        }
        if (authors.isEmpty()) {
            throw new IllegalArgumentException("At least one author is required.");
        }
        if (draft.totalCopies() < 1) {
            throw new IllegalArgumentException("Total copies must be at least 1.");
        }
        if (draft.price() == null || draft.price().signum() < 0) {
            throw new IllegalArgumentException("Enter a valid non-negative replacement price.");
        }
        String isbn = clean(draft.isbn()).replaceAll("[ -]", "").toUpperCase(Locale.ROOT);
        if (!isbn.isEmpty() && !isbn.matches("(?:\\d{9}[\\dX]|\\d{13})")) {
            throw new IllegalArgumentException("ISBN must be 10 characters or 13 digits.");
        }
        String year = clean(draft.publicationYear());
        if (!year.isEmpty()) {
            try {
                int numericYear = Integer.parseInt(year);
                if (numericYear < 1000 || numericYear > LocalDate.now().getYear() + 1) {
                    throw new IllegalArgumentException("Publication year must be between 1000 and next year.");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Publication year must be a whole number.");
            }
        }
        BigDecimal price = draft.price().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new Book(clean(draft.bookId()), isbn, title, authors, clean(draft.publisher()), year, clean(draft.edition()), clean(draft.category()),
                clean(draft.language()), clean(draft.shelfLocation()), price, draft.totalCopies(),
                draft.availableCopies(), draft.dateAdded());
    }

    private static void ensureUniqueIsbn(String isbn, String currentBookId, List<Book> books) {
        if (!isbn.isEmpty() && books.stream().anyMatch(book -> !book.bookId().equals(currentBookId)
                && book.isbn().equalsIgnoreCase(isbn))) {
            Book existing = books.stream().filter(book -> !book.bookId().equals(currentBookId)
                    && book.isbn().equalsIgnoreCase(isbn)).findFirst().orElseThrow();
            throw new DuplicateIsbnException(existing);
        }
    }

    private static int findIndex(List<Book> books, String bookId) {
        for (int index = 0; index < books.size(); index++) {
            if (books.get(index).bookId().equals(bookId)) {
                return index;
            }
        }
        return -1;
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
