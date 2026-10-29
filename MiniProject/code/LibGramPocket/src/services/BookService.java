package services;

import data.BookRepository;
import data.ContactRepository;
import data.LoanRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import model.Book;
import model.Book.Condition;
import model.Book.OwnershipType;
import model.Book.ReadingStatus;
import model.Loan;
import model.LoanDirection;
import model.LoanStatus;
import model.Contact;

public final class BookService {
    private static final Pattern ISBN_10 = Pattern.compile("[0-9]{9}[0-9X]");
    private static final Pattern ISBN_13 = Pattern.compile("[0-9]{13}");
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int MONEY_SCALE = 2;

    private final BookRepository books;
    private final BookChangeListener changeListener;
    private final LoanRepository loans;
    private final ContactRepository contacts;

    public BookService(BookRepository books) {
        this(books, BookChangeListener.NO_OP, LoanRepository.empty(), ContactRepository.empty());
    }

    public BookService(BookRepository books, BookChangeListener changeListener) {
        this(books, changeListener, LoanRepository.empty(), ContactRepository.empty());
    }

    public BookService(BookRepository books, LoanRepository loans, ContactRepository contacts) {
        this(books, BookChangeListener.NO_OP, loans, contacts);
    }

    public BookService(BookRepository books, BookChangeListener changeListener, LoanRepository loans,
            ContactRepository contacts) {
        this.books = Objects.requireNonNull(books, "books");
        this.changeListener = Objects.requireNonNull(changeListener, "changeListener");
        this.loans = Objects.requireNonNull(loans, "loans");
        this.contacts = Objects.requireNonNull(contacts, "contacts");
    }

    /**
     * Adds an owned book, assigning its ID, date, availability and default
     * statuses.
     */
    public Book add(Book draft) throws IOException {
        Book normalized = normalize(draft);
        List<Book> records = new ArrayList<>(books.findAll());
        ensureUniqueIsbn(normalized.isbn(), null, OwnershipType.OWNED, false, records);
        Book created = new Book(UUID.randomUUID().toString(), OwnershipType.OWNED, "", normalized.isbn(),
                normalized.title(), normalized.authors(), normalized.publisher(), normalized.publicationYear(),
                normalized.edition(), normalized.category(), normalized.language(), normalized.price(),
                normalized.totalCopies(), normalized.totalCopies(), normalized.condition(), normalized.readingStatus(),
                normalized.notes(), LocalDate.now(), false);
        records.add(created);
        books.saveAll(records);
        changeListener.onBookChanged(BookChangeListener.Change.ADDED, created);
        return created;
    }

    /**
     * Validates and prepares, but does not persist, a new borrowed catalog record.
     */
    public Book prepareBorrowedBook(Book draft, String lenderContactId) throws IOException {
        String lenderId = clean(lenderContactId);
        if (lenderId.isEmpty()
                || contacts.findAll().stream().noneMatch(contact -> contact.contactId().equals(lenderId))) {
            throw new IllegalArgumentException("Choose a lender that exists.");
        }
        Book normalized = normalize(draft);
        List<Book> records = books.findAll();
        ensureUniqueIsbn(normalized.isbn(), null, OwnershipType.BORROWED, false, records);
        return new Book(UUID.randomUUID().toString(), OwnershipType.BORROWED, lenderId, normalized.isbn(),
                normalized.title(), normalized.authors(), normalized.publisher(), normalized.publicationYear(),
                normalized.edition(), normalized.category(), normalized.language(), normalized.price(), 1, 1,
                normalized.condition(), normalized.readingStatus(), normalized.notes(), LocalDate.now(), false);
    }

    /**
     * Updates an owned book while preserving its ID, date added and copies already
     * out.
     */
    public Book update(Book draft) throws IOException {
        Book normalized = normalize(draft);
        List<Book> records = new ArrayList<>(books.findAll());
        int index = findIndex(records, draft.bookId());
        if (index < 0) {
            throw new IllegalArgumentException("The selected book no longer exists.");
        }
        Book existing = records.get(index);
        ensureUniqueIsbn(normalized.isbn(), existing.bookId(), existing.ownershipType(), existing.archived(), records);
        if (existing.ownershipType() == OwnershipType.BORROWED) {
            Book updated = new Book(existing.bookId(), OwnershipType.BORROWED, existing.lenderContactId(),
                    normalized.isbn(), normalized.title(), normalized.authors(), normalized.publisher(),
                    normalized.publicationYear(), normalized.edition(), normalized.category(), normalized.language(),
                    normalized.price(), 1, 1, normalized.condition(), normalized.readingStatus(), normalized.notes(),
                    existing.dateAdded(), existing.archived());
            records.set(index, updated);
            books.saveAll(records);
            changeListener.onBookChanged(BookChangeListener.Change.UPDATED, updated);
            return updated;
        }
        if (existing.ownershipType() != OwnershipType.OWNED) {
            throw new IllegalArgumentException("The selected book cannot be updated.");
        }
        int copiesOut = Math.max(existing.totalCopies() - existing.availableCopies(),
                activeLendsForBook(existing.bookId()).size());
        if (normalized.totalCopies() < copiesOut) {
            throw new IllegalArgumentException("Copies owned cannot be less than the " + copiesOut
                    + " copies currently out.");
        }
        Book updated = new Book(existing.bookId(), OwnershipType.OWNED, "", normalized.isbn(), normalized.title(),
                normalized.authors(), normalized.publisher(), normalized.publicationYear(), normalized.edition(),
                normalized.category(), normalized.language(), normalized.price(), normalized.totalCopies(),
                normalized.totalCopies() - copiesOut, normalized.condition(), normalized.readingStatus(),
                normalized.notes(), existing.dateAdded(), false);
        records.set(index, updated);
        books.saveAll(records);
        changeListener.onBookChanged(BookChangeListener.Change.UPDATED, updated);
        return updated;
    }

    /** Removes an owned book unless any copies are currently out. */
    public Book remove(String bookId) throws IOException {
        List<Book> records = new ArrayList<>(books.findAll());
        int index = findIndex(records, bookId);
        if (index < 0) {
            throw new IllegalArgumentException("The selected book no longer exists.");
        }
        Book removed = records.get(index);
        List<Loan> activeBorrows = activeBorrowsForBook(bookId);
        if (!activeBorrows.isEmpty()) {
            throw new IllegalArgumentException("Cannot remove this borrowed book while it is still borrowed. "
                    + "Return it to the lender first.");
        }
        List<Loan> activeLends = activeLendsForBook(bookId);
        if (!activeLends.isEmpty()) {
            String borrowerNames = activeLends.stream().map(loan -> contactName(loan.contactId()))
                    .distinct().reduce((left, right) -> left + ", " + right).orElse("a contact");
            throw new IllegalArgumentException("Cannot remove this book while it is lent to " + borrowerNames + ".");
        }
        if (removed.availableCopies() < removed.totalCopies()) {
            int copiesOut = removed.totalCopies() - removed.availableCopies();
            throw new IllegalArgumentException("Cannot remove this book while " + copiesOut
                    + (copiesOut == 1 ? " copy is" : " copies are") + " out.");
        }
        records.remove(index);
        books.saveAll(records);
        changeListener.onBookChanged(BookChangeListener.Change.REMOVED, removed);
        return removed;
    }

    /** Finds one book by its account-local ID. */
    public Optional<Book> getById(String bookId) throws IOException {
        return books.findAll().stream().filter(book -> book.bookId().equals(bookId)).findFirst();
    }

    /**
     * Lists active owned and borrowed books sorted by title, case-insensitively.
     */
    public List<Book> listSorted() throws IOException {
        return listSorted(false);
    }

    /** Lists catalog books, optionally including returned borrowed records. */
    public List<Book> listSorted(boolean showReturnedBorrowed) throws IOException {
        return books.findAll().stream()
                .filter(book -> book.ownershipType() == OwnershipType.OWNED && !book.archived()
                        || book.ownershipType() == OwnershipType.BORROWED
                                && (showReturnedBorrowed || !book.archived()))
                .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.title(), right.title()))
                .toList();
    }

    /**
     * Searches owned books by field and optionally filters out books with no
     * available copies.
     */
    public List<Book> search(BookSearchField field, String query, boolean availableOnly) throws IOException {
        return search(field, query, availableOnly, false);
    }

    /**
     * Searches visible catalog books and optionally includes returned borrowed
     * books.
     */
    public List<Book> search(BookSearchField field, String query, boolean availableOnly,
            boolean showReturnedBorrowed) throws IOException {
        Objects.requireNonNull(field, "field");
        String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);
        return listSorted(showReturnedBorrowed).stream()
                .filter(book -> !availableOnly || book.availableCopies() > 0)
                .filter(book -> normalizedQuery.isEmpty() || matches(book, field, normalizedQuery))
                .toList();
    }

    private static Book normalize(Book draft) {
        Objects.requireNonNull(draft, "book");
        String title = clean(draft.title());
        String authors = clean(draft.authors());
        if (title.isEmpty()) {
            throw new IllegalArgumentException("Title is required.");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("Title must be 200 characters or fewer.");
        }
        if (authors.isEmpty()) {
            throw new IllegalArgumentException("At least one author is required.");
        }
        if (draft.totalCopies() < 1) {
            throw new IllegalArgumentException("Copies owned must be a whole number of at least 1.");
        }
        BigDecimal price = draft.price() == null ? BigDecimal.ZERO : draft.price();
        if (price.signum() < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        String isbn = normalizeIsbn(draft.isbn());
        validateIsbn(isbn);
        String year = clean(draft.publicationYear());
        validateYear(year);
        return new Book(clean(draft.bookId()), OwnershipType.OWNED, "", isbn, title, authors,
                clean(draft.publisher()), year, clean(draft.edition()), clean(draft.category()),
                clean(draft.language()), price.setScale(MONEY_SCALE, RoundingMode.HALF_UP), draft.totalCopies(),
                draft.availableCopies(), draft.condition() == null ? Condition.GOOD : draft.condition(),
                draft.readingStatus() == null ? ReadingStatus.UNREAD : draft.readingStatus(),
                clean(draft.notes()), draft.dateAdded(), false);
    }

    private static String normalizeIsbn(String isbn) {
        return clean(isbn).replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    private static void validateIsbn(String isbn) {
        if (!isbn.isEmpty() && !ISBN_10.matcher(isbn).matches() && !ISBN_13.matcher(isbn).matches()) {
            throw new IllegalArgumentException("ISBN must be 10 characters or 13 digits.");
        }
    }

    private static void validateYear(String year) {
        if (year.isEmpty()) {
            return;
        }
        try {
            int publicationYear = Integer.parseInt(year);
            if (publicationYear < 1000 || publicationYear > LocalDate.now().getYear() + 1) {
                throw new IllegalArgumentException("Publication year must be between 1000 and next year.");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Publication year must be a whole number.");
        }
    }

    private static void ensureUniqueIsbn(String isbn, String currentBookId, OwnershipType ownershipType,
            boolean archived, List<Book> records) {
        if (isbn.isEmpty()) {
            return;
        }
        records.stream().filter(book -> !book.bookId().equals(currentBookId))
                .filter(book -> book.ownershipType() == ownershipType)
                .filter(book -> ownershipType == OwnershipType.OWNED || !book.archived() && !archived)
                .filter(book -> normalizeIsbn(book.isbn()).equals(isbn)).findFirst()
                .ifPresent(existing -> {
                    throw new DuplicateIsbnException(existing);
                });
    }

    private static boolean matches(Book book, BookSearchField field, String query) {
        return switch (field) {
            case ALL -> List.of(book.title(), book.authors(), book.isbn(), book.publisher(), book.category(),
                    book.language(), book.publicationYear(), readingStatusName(book.readingStatus()))
                    .stream().anyMatch(value -> contains(value, query));
            case TITLE -> contains(book.title(), query);
            case AUTHOR -> contains(book.authors(), query);
            case ISBN -> contains(book.isbn(), normalizeIsbn(query));
            case PUBLISHER -> contains(book.publisher(), query);
            case CATEGORY -> contains(book.category(), query);
            case LANGUAGE -> contains(book.language(), query);
            case YEAR -> contains(book.publicationYear(), query);
            case READING_STATUS -> contains(readingStatusName(book.readingStatus()), query);
        };
    }

    private static boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static String readingStatusName(ReadingStatus status) {
        String value = status.name();
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private static int findIndex(List<Book> records, String bookId) {
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index).bookId().equals(bookId)) {
                return index;
            }
        }
        return -1;
    }

    private List<Loan> activeLendsForBook(String bookId) throws IOException {
        return loans.findAll().stream().filter(loan -> loan.direction() == LoanDirection.LENT)
                .filter(loan -> loan.status() == LoanStatus.ACTIVE).filter(loan -> loan.bookId().equals(bookId))
                .toList();
    }

    private List<Loan> activeBorrowsForBook(String bookId) throws IOException {
        return loans.findAll().stream().filter(loan -> loan.direction() == LoanDirection.BORROWED)
                .filter(loan -> loan.status() == LoanStatus.ACTIVE).filter(loan -> loan.bookId().equals(bookId))
                .toList();
    }

    private String contactName(String contactId) {
        return contacts.findAll().stream().filter(contact -> contact.contactId().equals(contactId))
                .map(Contact::name).findFirst().orElse("a contact");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}