package services;

import data.BookRepository;
import data.ContactRepository;
import data.LoanRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import model.Bill;
import model.Book;
import model.Book.Condition;
import model.Book.OwnershipType;
import model.Contact;
import model.Loan;
import model.LoanDirection;
import model.LoanStatus;
import model.PaymentStatus;
import model.PricingTerms;
import model.ReturnCondition;

public final class LendingService {
    private final BookRepository books;
    private final LoanRepository loans;
    private final ContactRepository contacts;
    private final SettingsService settings;
    private final LoanEventListener listener;
    private final int correctedBookCount;

    public LendingService(BookRepository books, LoanRepository loans, ContactRepository contacts,
            SettingsService settings) throws IOException {
        this(books, loans, contacts, settings, LoanEventListener.NO_OP);
    }

    public LendingService(BookRepository books, LoanRepository loans, ContactRepository contacts,
            SettingsService settings, LoanEventListener listener) throws IOException {
        this.books = Objects.requireNonNull(books, "books");
        this.loans = Objects.requireNonNull(loans, "loans");
        this.contacts = Objects.requireNonNull(contacts, "contacts");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.listener = Objects.requireNonNull(listener, "listener");
        correctedBookCount = repairInventory();
    }

    /** Returns the number of book counts repaired during construction. */
    public int correctedBookCount() {
        return correctedBookCount;
    }

    /** Lends all requested books to one contact or rejects the entire request. */
    public List<Loan> lend(String contactId, List<LendRequest> requests, PricingTerms terms) throws IOException {
        return createLoans(LoanDirection.LENT, "lend", contactId, requests, terms);
    }

    /**
     * Records all requested books borrowed from one contact or rejects the request.
     */
    public List<Loan> borrow(String contactId, List<LendRequest> requests, PricingTerms terms) throws IOException {
        return createLoans(LoanDirection.BORROWED, "borrow", contactId, requests, terms);
    }

    /**
     * Saves a new borrowed book and its active loan together, or applies neither.
     */
    public Loan borrowNewBook(Book borrowedBook, LocalDate dueDate, PricingTerms terms) throws IOException {
        Objects.requireNonNull(borrowedBook, "borrowedBook");
        Objects.requireNonNull(terms, "terms");
        List<Book> currentBooks = books.findAll();
        List<Loan> currentLoans = loans.findAll();
        List<Contact> currentContacts = contacts.findAll();
        List<String> failures = new ArrayList<>();
        String lenderId = borrowedBook.lenderContactId();
        Contact lender = currentContacts.stream().filter(contact -> contact.contactId().equals(lenderId))
                .findFirst().orElse(null);
        if (lender == null) {
            failures.add("Choose a lender that exists.");
        }
        if (borrowedBook.ownershipType() != OwnershipType.BORROWED || borrowedBook.archived()
                || borrowedBook.totalCopies() != 1 || borrowedBook.availableCopies() != 1) {
            failures.add("A new borrow must be an active borrowed book with exactly one available copy.");
        }
        if (currentBooks.stream().anyMatch(book -> book.bookId().equals(borrowedBook.bookId()))) {
            failures.add("This borrowed book is already in the catalog.");
        }
        if (dueDate == null || dueDate.isBefore(LocalDate.now())) {
            failures.add("Due date must be today or later.");
        }
        if (lender != null) {
            String title = borrowedBook.title().trim();
            String authors = borrowedBook.authors().trim();
            boolean duplicateEntry = currentLoans.stream()
                    .filter(loan -> loan.direction() == LoanDirection.BORROWED && loan.status() == LoanStatus.ACTIVE)
                    .filter(loan -> loan.contactId().equals(lenderId))
                    .map(loan -> currentBooks.stream().filter(book -> book.bookId().equals(loan.bookId()))
                            .findFirst().orElse(null))
                    .filter(Objects::nonNull)
                    .anyMatch(book -> book.title().trim().equalsIgnoreCase(title)
                            && book.authors().trim().equalsIgnoreCase(authors));
            if (duplicateEntry) {
                failures.add("An active borrow from this lender already exists for this title and authors.");
            }
        }
        if (!borrowedBook.isbn().isBlank() && currentBooks.stream()
                .filter(book -> book.ownershipType() == OwnershipType.BORROWED && !book.archived())
                .anyMatch(book -> book.isbn().replaceAll("[\\s-]", "")
                        .equalsIgnoreCase(borrowedBook.isbn().replaceAll("[\\s-]", "")))) {
            failures.add("Another active borrowed book already uses this ISBN.");
        }
        if (!failures.isEmpty()) {
            throw new LendingValidationException(failures);
        }

        LocalDate issueDate = LocalDate.now();
        Loan created = new Loan(UUID.randomUUID().toString(), LoanDirection.BORROWED, borrowedBook.bookId(),
                borrowedBook.title(), lenderId, issueDate, dueDate, null, LoanStatus.ACTIVE,
                ChargeCalculator.baseCharge(terms), null, null, null, null, PaymentStatus.NONE, terms.toField());
        List<Book> updatedBooks = new ArrayList<>(currentBooks);
        updatedBooks.add(borrowedBook);
        commit(updatedBooks, currentLoans, append(currentLoans, List.of(created)));
        try {
            settings.saveLastPricing("borrow", terms);
        } catch (IOException | RuntimeException exception) {
            rollback(currentBooks, currentLoans, exception);
            throw exception;
        }
        listener.onLoanChanged(created, LoanEventListener.Change.BORROWED);
        return created;
    }

    private List<Loan> createLoans(LoanDirection direction, String settingsDirection, String contactId,
            List<LendRequest> requests, PricingTerms terms) throws IOException {
        Objects.requireNonNull(requests, "requests");
        Objects.requireNonNull(terms, "terms");
        List<Book> currentBooks = books.findAll();
        List<Loan> currentLoans = loans.findAll();
        List<Contact> currentContacts = contacts.findAll();
        List<String> failures = new ArrayList<>();
        Contact contact = currentContacts.stream().filter(item -> item.contactId().equals(contactId))
                .findFirst().orElse(null);
        if (contact == null) {
            failures.add("Choose a contact that exists.");
        }
        if (requests.isEmpty()) {
            failures.add(direction == LoanDirection.LENT ? "Add at least one book to lend."
                    : "Add at least one book to borrow.");
        }

        Map<String, Book> bookById = new HashMap<>();
        currentBooks.forEach(book -> bookById.put(book.bookId(), book));
        Set<String> requestedIds = new HashSet<>();
        for (LendRequest request : requests) {
            if (!requestedIds.add(request.bookId())) {
                failures.add("A book can only appear once in a transaction: " + request.bookId());
                continue;
            }
            Book book = bookById.get(request.bookId());
            if (book == null) {
                failures.add("The selected book no longer exists: " + request.bookId());
                continue;
            }
            if (book.archived()) {
                failures.add("\"" + book.title() + "\" is archived and cannot be used in a new transaction.");
            }
            if (direction == LoanDirection.LENT && book.ownershipType() != OwnershipType.OWNED) {
                failures.add("\"" + book.title() + "\" is borrowed and cannot be lent out.");
            }
            if (direction == LoanDirection.BORROWED && book.ownershipType() != OwnershipType.BORROWED) {
                failures.add("\"" + book.title() + "\" is not a borrowed catalog entry.");
            }
            if (direction == LoanDirection.LENT && book.availableCopies() < 1) {
                failures.add("No copies of \"" + book.title() + "\" are available.");
            }
            if (contact != null && currentLoans.stream().anyMatch(loan -> loan.status() == LoanStatus.ACTIVE
                    && loan.direction() == direction && loan.bookId().equals(book.bookId())
                    && loan.contactId().equals(contact.contactId()))) {
                failures.add(contact.name() + " already has an active "
                        + (direction == LoanDirection.LENT ? "lend" : "borrow") + " of \"" + book.title() + "\".");
            }
            if (direction == LoanDirection.BORROWED && !book.lenderContactId().equals(contactId)) {
                failures.add("\"" + book.title() + "\" is recorded with a different lender.");
            }
            if (direction == LoanDirection.BORROWED && currentLoans.stream()
                    .filter(loan -> loan.direction() == LoanDirection.BORROWED && loan.status() == LoanStatus.ACTIVE)
                    .filter(loan -> loan.contactId().equals(contactId) && !loan.bookId().equals(book.bookId()))
                    .map(loan -> bookById.get(loan.bookId())).filter(Objects::nonNull)
                    .anyMatch(existing -> existing.title().trim().equalsIgnoreCase(book.title().trim())
                            && existing.authors().trim().equalsIgnoreCase(book.authors().trim()))) {
                failures.add("An active borrow from this lender already exists for this title and authors.");
            }
            if (direction == LoanDirection.BORROWED && !book.isbn().isBlank()
                    && currentLoans.stream().filter(loan -> loan.direction() == LoanDirection.BORROWED
                            && loan.status() == LoanStatus.ACTIVE && !loan.bookId().equals(book.bookId()))
                            .map(loan -> bookById.get(loan.bookId())).filter(Objects::nonNull)
                            .anyMatch(existing -> existing.isbn().replaceAll("[\\s-]", "")
                                    .equalsIgnoreCase(book.isbn().replaceAll("[\\s-]", "")))) {
                failures.add("Another active borrowed book already uses this ISBN.");
            }
            if (request.dueDate() == null || request.dueDate().isBefore(LocalDate.now())) {
                failures.add("Due date for \"" + book.title() + "\" must be today or later.");
            }
        }
        if (!failures.isEmpty()) {
            throw new LendingValidationException(failures);
        }

        LocalDate issueDate = LocalDate.now();
        List<Loan> created = new ArrayList<>();
        List<Book> updatedBooks = new ArrayList<>(currentBooks);
        for (LendRequest request : requests) {
            Book book = bookById.get(request.bookId());
            Loan loan = new Loan(UUID.randomUUID().toString(), direction, book.bookId(), book.title(),
                    contact.contactId(), issueDate, request.dueDate(), null, LoanStatus.ACTIVE,
                    ChargeCalculator.baseCharge(terms), null, null, null, null, PaymentStatus.NONE, terms.toField());
            created.add(loan);
            if (direction == LoanDirection.LENT) {
                int index = indexOfBook(updatedBooks, book.bookId());
                updatedBooks.set(index, withBookState(book, book.availableCopies() - 1, book.condition()));
            }
        }
        commit(updatedBooks, currentLoans, append(currentLoans, created));
        try {
            settings.saveLastPricing(settingsDirection, terms);
        } catch (IOException | RuntimeException exception) {
            rollback(currentBooks, currentLoans, exception);
            throw exception;
        }
        LoanEventListener.Change change = direction == LoanDirection.LENT
                ? LoanEventListener.Change.LENT
                : LoanEventListener.Change.BORROWED;
        created.forEach(loan -> listener.onLoanChanged(loan, change));
        return List.copyOf(created);
    }

    /** Previews a return bill without modifying stored records. */
    public Bill previewReturn(Loan loan, LocalDate returnDate, ReturnCondition condition, BigDecimal damageAmount) {
        if (loan.direction() == LoanDirection.BORROWED) {
            return ChargeCalculator.calculateReturn(PricingTerms.parse(loan.pricingSnapshot()), loan.issueDate(),
                    loan.dueDate(), returnDate, ReturnCondition.GOOD, BigDecimal.ZERO);
        }
        return ChargeCalculator.calculateReturn(PricingTerms.parse(loan.pricingSnapshot()), loan.issueDate(),
                loan.dueDate(), returnDate, condition, damageAmount);
    }

    /** Returns one active loan and its book in a repository transaction. */
    public Loan returnLoan(String loanId, LocalDate returnDate, ReturnCondition condition, BigDecimal damageAmount,
            boolean markPaid) throws IOException {
        return returnLoans(List.of(new ReturnRequest(loanId, returnDate, condition, damageAmount)), markPaid)
                .getFirst();
    }

    /** Returns several active loans using one book/loan persistence transaction. */
    public List<Loan> returnLoans(List<ReturnRequest> requests, boolean markPaid) throws IOException {
        if (requests.isEmpty()) {
            throw new IllegalArgumentException("Select at least one loan to return.");
        }
        // ASSUMPTION: One return action records a single shared return date.
        LocalDate returnDate = requests.getFirst().returnDate();
        List<Loan> currentLoans = loans.findAll();
        List<Book> currentBooks = books.findAll();
        Set<String> seenLoanIds = new HashSet<>();
        Map<String, Book> updatedBooksById = new HashMap<>();
        List<Loan> returnedLoans = new ArrayList<>();
        for (ReturnRequest request : requests) {
            if (!seenLoanIds.add(request.loanId())) {
                throw new IllegalArgumentException("A loan can only be returned once per action.");
            }
            Loan existing = currentLoans.stream().filter(loan -> loan.loanId().equals(request.loanId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Loan not found."));
            if (existing.status() != LoanStatus.ACTIVE) {
                throw new IllegalArgumentException("This loan has already been returned.");
            }
            if (!returnDate.equals(request.returnDate())) {
                throw new IllegalArgumentException("All selected loans must use the same return date.");
            }
            Bill bill = previewReturn(existing, request.returnDate(), request.condition(), request.damageAmount());
            Book book = updatedBooksById.getOrDefault(existing.bookId(), currentBooks.stream()
                    .filter(item -> item.bookId().equals(existing.bookId())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("The loan book no longer exists.")));
            Book returnedBook = withBookState(book,
                    existing.direction() == LoanDirection.LENT
                            ? Math.min(book.totalCopies(), book.availableCopies() + 1)
                            : book.availableCopies(),
                    request.condition() == ReturnCondition.SEVERE_DAMAGE ? Condition.DAMAGED : book.condition());
            if (existing.direction() == LoanDirection.BORROWED) {
                returnedBook = withArchived(returnedBook, true);
            }
            updatedBooksById.put(book.bookId(), returnedBook);
            returnedLoans.add(new Loan(existing.loanId(), existing.direction(), existing.bookId(),
                    existing.bookTitleSnapshot(), existing.contactId(), existing.issueDate(), existing.dueDate(),
                    request.returnDate(), LoanStatus.RETURNED, existing.baseCharge(), billAmount(bill, "Rent"),
                    billAmount(bill, "Overdue penalty"), billAmount(bill, "Damage"),
                    existing.direction() == LoanDirection.BORROWED ? ReturnCondition.GOOD : request.condition(),
                    paymentStatus(bill.total(), markPaid), existing.pricingSnapshot()));
        }
        List<Book> updatedBooks = currentBooks.stream()
                .map(book -> updatedBooksById.getOrDefault(book.bookId(), book)).toList();
        Map<String, Loan> returnedById = new HashMap<>();
        returnedLoans.forEach(loan -> returnedById.put(loan.loanId(), loan));
        List<Loan> updatedLoans = currentLoans.stream()
                .map(loan -> returnedById.getOrDefault(loan.loanId(), loan)).toList();
        commit(updatedBooks, currentLoans, updatedLoans);
        returnedLoans.forEach(loan -> listener.onLoanChanged(loan, LoanEventListener.Change.RETURNED));
        return List.copyOf(returnedLoans);
    }

    /** Marks a returned owed loan as paid. */
    public Loan markAsPaid(String loanId) throws IOException {
        List<Loan> current = loans.findAll();
        Loan existing = current.stream().filter(loan -> loan.loanId().equals(loanId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loan not found."));
        if (existing.status() != LoanStatus.RETURNED || existing.paymentStatus() != PaymentStatus.OWED) {
            throw new IllegalArgumentException("Only a returned loan with an amount owed can be marked paid.");
        }
        Loan paid = withPaymentStatus(existing, PaymentStatus.PAID);
        loans.saveAll(current.stream().map(loan -> loan.loanId().equals(loanId) ? paid : loan).toList());
        listener.onLoanChanged(paid, LoanEventListener.Change.PAYMENT_UPDATED);
        return paid;
    }

    /** Lists active LENT loans ordered by due date. */
    public List<Loan> activeLends() throws IOException {
        return loans.findAll().stream().filter(loan -> loan.direction() == LoanDirection.LENT)
                .filter(loan -> loan.status() == LoanStatus.ACTIVE)
                .sorted(Comparator.comparing(Loan::dueDate)).toList();
    }

    /** Lists active BORROWED loans ordered by due date. */
    public List<Loan> activeBorrows() throws IOException {
        return loans.findAll().stream().filter(loan -> loan.direction() == LoanDirection.BORROWED)
                .filter(loan -> loan.status() == LoanStatus.ACTIVE)
                .sorted(Comparator.comparing(Loan::dueDate)).toList();
    }

    /** Lists returned LENT loans ordered by most recent return date. */
    public List<Loan> lendHistory() throws IOException {
        return loans.findAll().stream().filter(loan -> loan.direction() == LoanDirection.LENT)
                .filter(loan -> loan.status() == LoanStatus.RETURNED)
                .sorted(Comparator.comparing(Loan::returnDate).reversed()).toList();
    }

    /** Lists returned BORROWED loans ordered by most recent return date. */
    public List<Loan> borrowHistory() throws IOException {
        return loans.findAll().stream().filter(loan -> loan.direction() == LoanDirection.BORROWED)
                .filter(loan -> loan.status() == LoanStatus.RETURNED)
                .sorted(Comparator.comparing(Loan::returnDate).reversed()).toList();
    }

    /** Lists active LENT loans whose due date has passed. */
    public List<Loan> overdueLends(LocalDate date) throws IOException {
        return activeLends().stream().filter(loan -> LoanRules.daysOverdue(loan.dueDate(), date) > 0).toList();
    }

    /** Lists active LENT loans due within the specified window. */
    public List<Loan> dueSoonLends(LocalDate date, int windowDays) throws IOException {
        return activeLends().stream().filter(loan -> LoanRules.isDueSoon(loan, date, windowDays)).toList();
    }

    /** Lists all loan directions for a contact, newest first. */
    public List<Loan> loansByContact(String contactId) throws IOException {
        return loans.findAll().stream().filter(loan -> loan.contactId().equals(contactId))
                .sorted(Comparator.comparing(Loan::issueDate).reversed()).toList();
    }

    /** Lists all loan directions for a book, newest first. */
    public List<Loan> loansByBook(String bookId) throws IOException {
        return loans.findAll().stream().filter(loan -> loan.bookId().equals(bookId))
                .sorted(Comparator.comparing(Loan::issueDate).reversed()).toList();
    }

    /** Lists active LENT loans for a contact. */
    public List<Loan> activeLendsByContact(String contactId) throws IOException {
        return loansByContact(contactId).stream().filter(loan -> loan.direction() == LoanDirection.LENT)
                .filter(loan -> loan.status() == LoanStatus.ACTIVE).toList();
    }

    /** Lists active BORROWED loans for a contact. */
    public List<Loan> activeBorrowsByContact(String contactId) throws IOException {
        return loansByContact(contactId).stream().filter(loan -> loan.direction() == LoanDirection.BORROWED)
                .filter(loan -> loan.status() == LoanStatus.ACTIVE).toList();
    }

    /** Finds one account-local loan by ID. */
    public Optional<Loan> findLoan(String loanId) throws IOException {
        return loans.findAll().stream().filter(loan -> loan.loanId().equals(loanId)).findFirst();
    }

    /** Calculates accrued charges for an active loan as of a date. */
    public Bill amountSoFar(Loan loan, LocalDate asOfDate) {
        return ChargeCalculator.calculateAccrued(PricingTerms.parse(loan.pricingSnapshot()), loan.issueDate(),
                loan.dueDate(), asOfDate);
    }

    private int repairInventory() throws IOException {
        List<Book> currentBooks = books.findAll();
        Map<String, Long> activeByBook = new HashMap<>();
        for (Loan loan : loans.findAll()) {
            if (loan.direction() == LoanDirection.LENT && loan.status() == LoanStatus.ACTIVE) {
                activeByBook.merge(loan.bookId(), 1L, Long::sum);
            }
        }
        int corrected = 0;
        List<Book> repaired = new ArrayList<>(currentBooks);
        for (int index = 0; index < repaired.size(); index++) {
            Book book = repaired.get(index);
            if (book.ownershipType() != OwnershipType.OWNED) {
                continue;
            }
            // ASSUMPTION: Corrupt over-lending is repaired to zero available, never a
            // negative count.
            long available = Math.max(0, book.totalCopies() - activeByBook.getOrDefault(book.bookId(), 0L));
            if (book.availableCopies() != available) {
                System.getLogger(LendingService.class.getName()).log(System.Logger.Level.WARNING,
                        "Correcting available copies for book " + book.bookId());
                repaired.set(index, withBookState(book, (int) available, book.condition()));
                corrected++;
            }
        }
        if (corrected > 0) {
            books.saveAll(repaired);
        }
        return corrected;
    }

    private void commit(List<Book> newBooks, List<Loan> oldLoans, List<Loan> newLoans) throws IOException {
        loans.saveAll(newLoans);
        try {
            books.saveAll(newBooks);
        } catch (IOException | RuntimeException exception) {
            rollbackLoans(oldLoans, exception);
            throw exception;
        }
    }

    private void rollback(List<Book> oldBooks, List<Loan> oldLoans, Throwable cause) {
        try {
            books.saveAll(oldBooks);
        } catch (IOException exception) {
            cause.addSuppressed(exception);
        }
        rollbackLoans(oldLoans, cause);
    }

    private void rollbackLoans(List<Loan> oldLoans, Throwable cause) {
        try {
            loans.saveAll(oldLoans);
        } catch (IOException exception) {
            cause.addSuppressed(exception);
        }
    }

    private static PaymentStatus paymentStatus(BigDecimal total, boolean markPaid) {
        if (total.signum() == 0) {
            return PaymentStatus.NONE;
        }
        return markPaid ? PaymentStatus.PAID : PaymentStatus.OWED;
    }

    private static BigDecimal billAmount(Bill bill, String label) {
        return bill.lines().stream().filter(line -> line.label().equals(label)).map(Bill.Line::amount)
                .findFirst().orElse(new BigDecimal("0.00"));
    }

    private static Loan withPaymentStatus(Loan loan, PaymentStatus status) {
        return new Loan(loan.loanId(), loan.direction(), loan.bookId(), loan.bookTitleSnapshot(), loan.contactId(),
                loan.issueDate(), loan.dueDate(), loan.returnDate(), loan.status(), loan.baseCharge(),
                loan.rentCharge(), loan.overduePenalty(), loan.damageCharge(), loan.returnCondition(), status,
                loan.pricingSnapshot());
    }

    private static Book withBookState(Book book, int availableCopies, Condition condition) {
        return new Book(book.bookId(), book.ownershipType(), book.lenderContactId(), book.isbn(), book.title(),
                book.authors(), book.publisher(), book.publicationYear(), book.edition(), book.category(),
                book.language(), book.price(), book.totalCopies(), availableCopies, condition, book.readingStatus(),
                book.notes(), book.dateAdded(), book.archived());
    }

    private static Book withArchived(Book book, boolean archived) {
        return new Book(book.bookId(), book.ownershipType(), book.lenderContactId(), book.isbn(), book.title(),
                book.authors(), book.publisher(), book.publicationYear(), book.edition(), book.category(),
                book.language(), book.price(), book.totalCopies(), book.availableCopies(), book.condition(),
                book.readingStatus(), book.notes(), book.dateAdded(), archived);
    }

    private static int indexOfBook(List<Book> records, String bookId) {
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index).bookId().equals(bookId)) {
                return index;
            }
        }
        throw new IllegalArgumentException("The selected book no longer exists.");
    }

    private static List<Loan> append(List<Loan> current, List<Loan> added) {
        List<Loan> result = new ArrayList<>(current);
        result.addAll(added);
        return List.copyOf(result);
    }

    public record LendRequest(String bookId, LocalDate dueDate) {
    }

    public record ReturnRequest(String loanId, LocalDate returnDate, ReturnCondition condition,
            BigDecimal damageAmount) {
    }
}