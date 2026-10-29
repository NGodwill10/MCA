import data.BookRepository;
import data.ContactRepository;
import data.CsvSettingsRepository;
import data.LoanRepository;
import java.io.IOException;
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
import model.Contact;
import model.Loan;
import model.LoanDirection;
import model.LoanStatus;
import model.PaymentStatus;
import model.PricingTerms;
import model.PricingTerms.RentUnit;
import model.ReturnCondition;
import services.LendingService;
import services.LendingValidationException;
import services.BookService;
import services.ContactService;
import services.LoanEventListener;
import services.SettingsService;

public final class LendingServiceTest {
    private static final PricingTerms TERMS = new PricingTerms(true, new BigDecimal("5.00"), true,
            RentUnit.WEEK, new BigDecimal("10.00"), true, new BigDecimal("1.00"), new BigDecimal("50.00"));

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("pocket-lending-test");
        try {
            verifyLendReturnAndSnapshot(root.resolve("account-a"));
            verifyValidationAndRollback(root.resolve("account-b"));
            verifyInventoryRepairAndIsolation(root.resolve("account-c"), root.resolve("account-d"));
            verifyLoanAwareCatalogAndContactRules(root.resolve("account-e"));
            verifyBorrowDirection(root.resolve("account-f"));
            System.out.println("LendingServiceTest passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyLendReturnAndSnapshot(Path folder) throws Exception {
        Files.createDirectories(folder);
        MemoryBooks books = new MemoryBooks(List.of(book("book-1", "The Book", 2, 2, OwnershipType.OWNED,
                new BigDecimal("300.00"))));
        MemoryLoans loans = new MemoryLoans();
        MemoryContacts contacts = new MemoryContacts(List.of(contact("contact-1", "Ravi")));
        SettingsService settings = settings(folder);
        List<LoanEventListener.Change> events = new ArrayList<>();
        LendingService service = new LendingService(books, loans, contacts, settings,
                (changedLoan, change) -> events.add(change));
        LocalDate due = LocalDate.now().plusDays(14);
        Loan loan = service.lend("contact-1", List.of(new LendingService.LendRequest("book-1", due)), TERMS)
                .getFirst();
        check(loan.baseCharge().equals(new BigDecimal("5.00")));
        check(books.findAll().getFirst().availableCopies() == 1);
        check(loan.pricingSnapshot().equals(TERMS.toField()));
        check(service.activeLends().size() == 1);
        check(service.dueSoonLends(LocalDate.now(), 14).size() == 1);
        check(service.overdueLends(LocalDate.now()).isEmpty());
        check(events.equals(List.of(LoanEventListener.Change.LENT)));
        expectValidation(() -> service.lend("contact-1",
                List.of(new LendingService.LendRequest("book-1", due)), TERMS));

        settings.save(java.util.Map.of("pricing.rent.amount", "99.00"));
        LocalDate returned = loan.issueDate().plusDays(8);
        LocalDate overdueDue = returned.minusDays(3);
        loans.replace(withDueDate(loan, overdueDue));
        Loan activeWithChangedDue = loans.findAll().getFirst();
        check(service.previewReturn(activeWithChangedDue, returned, ReturnCondition.GOOD, BigDecimal.ZERO)
                .total().equals(new BigDecimal("28.00")));
        Loan returnedLoan = service.returnLoan(loan.loanId(), returned, ReturnCondition.MINOR_DAMAGE,
                new BigDecimal("15.00"), false);
        check(returnedLoan.paymentStatus() == PaymentStatus.OWED);
        check(returnedLoan.rentCharge().equals(new BigDecimal("20.00")));
        check(returnedLoan.overduePenalty().equals(new BigDecimal("3.00")));
        check(returnedLoan.damageCharge().equals(new BigDecimal("15.00")));
        check(books.findAll().getFirst().availableCopies() == 2);
        check(service.markAsPaid(loan.loanId()).paymentStatus() == PaymentStatus.PAID);
        check(service.lendHistory().size() == 1);

        Loan freeLoan = service.lend("contact-1", List.of(new LendingService.LendRequest("book-1", due)),
                new PricingTerms(false, BigDecimal.ZERO, false, RentUnit.DAY, BigDecimal.ZERO,
                        false, BigDecimal.ZERO, BigDecimal.ZERO))
                .getFirst();
        Loan freeReturned = service.returnLoan(freeLoan.loanId(), LocalDate.now(), ReturnCondition.GOOD,
                BigDecimal.ZERO, true);
        check(freeReturned.paymentStatus() == PaymentStatus.NONE);

        Loan damageLoan = service.lend("contact-1", List.of(new LendingService.LendRequest("book-1", due)), TERMS)
                .getFirst();
        service.returnLoan(damageLoan.loanId(), LocalDate.now(), ReturnCondition.SEVERE_DAMAGE,
                new BigDecimal("300.00"), false);
        check(books.findAll().getFirst().condition() == Condition.DAMAGED);
        check(events.contains(LoanEventListener.Change.RETURNED));
        check(events.contains(LoanEventListener.Change.PAYMENT_UPDATED));
    }

    private static void verifyValidationAndRollback(Path folder) throws Exception {
        Files.createDirectories(folder);
        MemoryBooks books = new MemoryBooks(List.of(
                book("owned", "Owned", 1, 1, OwnershipType.OWNED, BigDecimal.ZERO),
                book("borrowed", "Borrowed", 1, 1, OwnershipType.BORROWED, BigDecimal.ZERO),
                book("empty", "Empty", 1, 0, OwnershipType.OWNED, BigDecimal.ZERO)));
        Loan existingEmptyBookLoan = new Loan("other-loan", LoanDirection.LENT, "empty", "Empty", "contact-2",
                LocalDate.now(), LocalDate.now().plusDays(3), null, LoanStatus.ACTIVE,
                BigDecimal.ZERO.setScale(2), null, null, null, null, PaymentStatus.NONE, TERMS.toField());
        MemoryLoans loans = new MemoryLoans(List.of(existingEmptyBookLoan));
        MemoryContacts contacts = new MemoryContacts(List.of(contact("contact-1", "Ravi"),
                contact("contact-2", "Asha")));
        LendingService service = new LendingService(books, loans, contacts, settings(folder));
        LocalDate due = LocalDate.now().plusDays(1);
        expectValidation(() -> service.lend("missing", List.of(new LendingService.LendRequest("owned", due)), TERMS));
        try {
            service.lend("contact-1", List.of(new LendingService.LendRequest("owned", due),
                    new LendingService.LendRequest("borrowed", due),
                    new LendingService.LendRequest("empty", due)), TERMS);
            throw new AssertionError("Invalid group should not be lent");
        } catch (LendingValidationException exception) {
            check(exception.failures().size() >= 2);
        }
        check(loans.findAll().size() == 1);
        check(books.findAll().getFirst().availableCopies() == 1);
        expectValidation(() -> service.lend("contact-1", List.of(
                new LendingService.LendRequest("owned", LocalDate.now().minusDays(1))), TERMS));

        loans.failNextSave = false;
        books.failNextSave = true;
        try {
            service.lend("contact-1", List.of(new LendingService.LendRequest("owned", due)), TERMS);
            throw new AssertionError("Book write failure should fail the lend");
        } catch (IOException expected) {
            check(loans.findAll().size() == 1);
            check(books.findAll().getFirst().availableCopies() == 1);
        }

        Loan loan = service.lend("contact-1", List.of(new LendingService.LendRequest("owned", due)), TERMS)
                .getFirst();
        books.failNextSave = true;
        try {
            service.returnLoan(loan.loanId(), LocalDate.now(), ReturnCondition.GOOD, BigDecimal.ZERO, false);
            throw new AssertionError("Book write failure should fail the return");
        } catch (IOException expected) {
            check(loans.findAll().stream().anyMatch(item -> item.loanId().equals(loan.loanId())
                    && item.status() == LoanStatus.ACTIVE));
            check(books.findAll().stream().filter(book -> book.bookId().equals("owned")).findFirst()
                    .orElseThrow().availableCopies() == 0);
        }
    }

    private static void verifyInventoryRepairAndIsolation(Path folderA, Path folderB) throws Exception {
        Files.createDirectories(folderA);
        Files.createDirectories(folderB);
        Loan active = new Loan("active", LoanDirection.LENT, "book", "Book", "contact", LocalDate.now(),
                LocalDate.now().plusDays(2), null, LoanStatus.ACTIVE, BigDecimal.ZERO.setScale(2), null, null,
                null, null, PaymentStatus.NONE, TERMS.toField());
        MemoryBooks booksA = new MemoryBooks(List.of(book("book", "Book", 3, 3, OwnershipType.OWNED,
                BigDecimal.ZERO)));
        MemoryLoans loansA = new MemoryLoans(List.of(active));
        LendingService accountA = new LendingService(booksA, loansA,
                new MemoryContacts(List.of(contact("contact", "Ravi"))), settings(folderA));
        LendingService accountB = new LendingService(new MemoryBooks(List.of()), new MemoryLoans(),
                new MemoryContacts(List.of()), settings(folderB));
        check(accountA.correctedBookCount() == 1);
        check(booksA.findAll().getFirst().availableCopies() == 2);
        check(accountA.activeLends().size() == 1);
        check(accountB.activeLends().isEmpty());
    }

    private static void verifyLoanAwareCatalogAndContactRules(Path folder) throws Exception {
        Files.createDirectories(folder);
        LocalDate issue = LocalDate.now();
        Loan activeA = new Loan("active-a", LoanDirection.LENT, "book", "Book", "contact-a", issue,
                issue.plusDays(5), null, LoanStatus.ACTIVE, BigDecimal.ZERO.setScale(2), null, null, null,
                null, PaymentStatus.NONE, TERMS.toField());
        Loan activeB = new Loan("active-b", LoanDirection.LENT, "book", "Book", "contact-b", issue,
                issue.plusDays(5), null, LoanStatus.ACTIVE, BigDecimal.ZERO.setScale(2), null, null, null,
                null, PaymentStatus.NONE, TERMS.toField());
        Loan owed = new Loan("owed", LoanDirection.LENT, "other-book", "Other book", "contact-owed", issue,
                issue, issue, LoanStatus.RETURNED, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ONE.setScale(2), ReturnCondition.MINOR_DAMAGE,
                PaymentStatus.OWED, TERMS.toField());
        Loan activeBorrow = new Loan("active-borrow", LoanDirection.BORROWED, "borrowed-book", "Borrowed book",
                "contact-borrow", issue, issue.plusDays(5), null, LoanStatus.ACTIVE, BigDecimal.ZERO.setScale(2),
                null, null, null, null, PaymentStatus.NONE, TERMS.toField());
        Loan owedBorrow = new Loan("owed-borrow", LoanDirection.BORROWED, "returned-book", "Returned book",
                "contact-owed-borrow", issue, issue, issue, LoanStatus.RETURNED, BigDecimal.ONE.setScale(2),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
                ReturnCondition.GOOD, PaymentStatus.OWED, TERMS.toField());
        MemoryLoans loans = new MemoryLoans(List.of(activeA, activeB, owed, activeBorrow, owedBorrow));
        MemoryBooks books = new MemoryBooks(List.of(
                book("book", "Book", 2, 2, OwnershipType.OWNED, BigDecimal.ZERO),
                book("other-book", "Other book", 1, 1, OwnershipType.OWNED, BigDecimal.ZERO)));
        MemoryContacts contacts = new MemoryContacts(List.of(contact("contact-a", "Asha"),
                contact("contact-b", "Ravi"), contact("contact-owed", "Mina"),
                contact("contact-borrow", "Lender"), contact("contact-owed-borrow", "Owed lender")));
        BookService bookService = new BookService(books, loans, contacts);
        ContactService contactService = new ContactService(contacts, loans);
        try {
            bookService.remove("book");
            throw new AssertionError("An active lend must block book removal");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("Asha") || expected.getMessage().contains("Ravi"));
        }
        Book book = books.findAll().getFirst();
        Book reduced = new Book(book.bookId(), book.ownershipType(), book.lenderContactId(), book.isbn(),
                book.title(), book.authors(), book.publisher(), book.publicationYear(), book.edition(),
                book.category(), book.language(), book.price(), 1, 1, book.condition(), book.readingStatus(),
                book.notes(), book.dateAdded(), book.archived());
        try {
            bookService.update(reduced);
            throw new AssertionError("Active loan count must block reducing total copies");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("copies currently out"));
        }
        expectBlockedDelete(() -> contactService.delete("contact-a"), "active lend blocks contact deletion");
        expectBlockedDelete(() -> contactService.delete("contact-owed"), "owed lend blocks contact deletion");
        expectBlockedDelete(() -> contactService.delete("contact-borrow"), "active borrow blocks contact deletion");
        expectBlockedDelete(() -> contactService.delete("contact-owed-borrow"), "owed borrow blocks contact deletion");
    }

    private static void verifyBorrowDirection(Path folder) throws Exception {
        Files.createDirectories(folder);
        Contact lender = contact("lender", "Sam");
        MemoryBooks books = new MemoryBooks(List.of(book("owned-copy", "Shared title", 1, 1,
                OwnershipType.OWNED, BigDecimal.ZERO)));
        MemoryLoans loans = new MemoryLoans();
        MemoryContacts contacts = new MemoryContacts(List.of(lender));
        List<LoanEventListener.Change> events = new ArrayList<>();
        LendingService service = new LendingService(books, loans, contacts, settings(folder),
                (loan, change) -> events.add(change));
        BookService bookService = new BookService(books, loans, contacts);
        Book draft = new Book("", OwnershipType.OWNED, "", "9780618260300", "Shared title", "Author", "",
                "", "", "", "", new BigDecimal("25.00"), 1, 1, Condition.GOOD,
                ReadingStatus.UNREAD, "", LocalDate.now(), false);
        Book borrowedBook = bookService.prepareBorrowedBook(draft, lender.contactId());
        check(borrowedBook.totalCopies() == 1 && borrowedBook.availableCopies() == 1);
        check(borrowedBook.lenderContactId().equals(lender.contactId()));
        try {
            bookService.prepareBorrowedBook(draft, "missing-lender");
            throw new AssertionError("A lender must exist");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("lender"));
        }
        Loan borrowed = service.borrowNewBook(borrowedBook, LocalDate.now().plusDays(14), TERMS);
        check(borrowed.direction() == LoanDirection.BORROWED);
        check(service.activeBorrows().size() == 1);
        check(books.findAll().stream().filter(book -> book.bookId().equals(borrowedBook.bookId()))
                .findFirst().orElseThrow().availableCopies() == 1);
        check(events.equals(List.of(LoanEventListener.Change.BORROWED)));
        try {
            service.lend(lender.contactId(), List.of(new LendingService.LendRequest(
                    borrowedBook.bookId(), LocalDate.now().plusDays(7))), TERMS);
            throw new AssertionError("A borrowed book cannot be lent onward");
        } catch (LendingValidationException expected) {
            check(expected.getMessage().contains("borrowed and cannot be lent out"));
        }
        expectBlockedDelete(() -> new ContactService(contacts, loans).delete(lender.contactId()),
                "active borrow blocks lender deletion");
        expectBlockedBookRemove(bookService, borrowedBook.bookId());
        Book editedBorrowed = bookService.update(new Book(borrowedBook.bookId(), OwnershipType.OWNED, "ignored",
                borrowedBook.isbn(), borrowedBook.title(), borrowedBook.authors(), borrowedBook.publisher(),
                borrowedBook.publicationYear(), borrowedBook.edition(), borrowedBook.category(),
                borrowedBook.language(), borrowedBook.price(), 5, 0, Condition.WORN, ReadingStatus.READING,
                "edited", borrowedBook.dateAdded(), false));
        check(editedBorrowed.ownershipType() == OwnershipType.BORROWED);
        check(editedBorrowed.lenderContactId().equals(lender.contactId()));
        check(editedBorrowed.totalCopies() == 1 && editedBorrowed.availableCopies() == 1);
        try {
            bookService.prepareBorrowedBook(draft, lender.contactId());
            throw new AssertionError("Two active borrowed books cannot share an ISBN");
        } catch (services.DuplicateIsbnException expected) {
            check(expected.getMessage().contains("ISBN"));
        }
        Book duplicateEntry = bookService.prepareBorrowedBook(new Book("", OwnershipType.OWNED, "", "",
                "Shared title", "Author", "", "", "", "", "", BigDecimal.ZERO, 1, 1,
                Condition.GOOD, ReadingStatus.UNREAD, "", LocalDate.now(), false), lender.contactId());
        expectValidation(() -> service.borrowNewBook(duplicateEntry, LocalDate.now().plusDays(7), TERMS));
        check(bookService.add(draft).ownershipType() == OwnershipType.OWNED);

        Book failedWriteBook = bookService.prepareBorrowedBook(new Book("", OwnershipType.OWNED, "", "",
                "Failed write", "Author", "", "", "", "", "", BigDecimal.ZERO, 1, 1,
                Condition.GOOD, ReadingStatus.UNREAD, "", LocalDate.now(), false), lender.contactId());
        books.failNextSave = true;
        try {
            service.borrowNewBook(failedWriteBook, LocalDate.now().plusDays(7), TERMS);
            throw new AssertionError("Book write failure should fail borrow creation");
        } catch (IOException expected) {
            check(books.findAll().stream().noneMatch(book -> book.bookId().equals(failedWriteBook.bookId())));
            check(loans.findAll().size() == 1);
        }

        LocalDate returnDate = borrowed.issueDate().plusDays(8);
        check(service.previewReturn(borrowed, returnDate, ReturnCondition.SEVERE_DAMAGE,
                new BigDecimal("90.00")).total().equals(new BigDecimal("25.00")));
        Loan returned = service.returnLoan(borrowed.loanId(), returnDate, ReturnCondition.SEVERE_DAMAGE,
                new BigDecimal("90.00"), false);
        check(returned.paymentStatus() == PaymentStatus.OWED);
        check(returned.damageCharge().equals(new BigDecimal("0.00")));
        check(service.markAsPaid(borrowed.loanId()).paymentStatus() == PaymentStatus.PAID);
        check(service.borrowHistory().size() == 1);
        Book archived = books.findAll().stream().filter(book -> book.bookId().equals(borrowedBook.bookId()))
                .findFirst().orElseThrow();
        check(archived.archived());
        check(archived.availableCopies() == 1);
        check(bookService.listSorted().stream().noneMatch(book -> book.bookId().equals(archived.bookId())));
        check(bookService.listSorted(true).stream().anyMatch(book -> book.bookId().equals(archived.bookId())));
        bookService.remove(archived.bookId());
    }

    private static void expectBlockedDelete(ThrowingAction action, String description) throws Exception {
        try {
            action.run();
            throw new AssertionError(description);
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("Cannot delete this contact"));
        }
    }

    private static void expectBlockedBookRemove(BookService books, String bookId) throws Exception {
        try {
            books.remove(bookId);
            throw new AssertionError("An active borrow must block book removal");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("Return it to the lender"));
        }
    }

    private static SettingsService settings(Path folder) throws IOException {
        return new SettingsService(new CsvSettingsRepository(folder.resolve("settings.txt")));
    }

    private static Book book(String id, String title, int total, int available, OwnershipType ownership,
            BigDecimal price) {
        return new Book(id, ownership, "", "", title, "Author", "", "", "", "", "", price,
                total, available, Condition.GOOD, ReadingStatus.UNREAD, "", LocalDate.now(), false);
    }

    private static Contact contact(String id, String name) {
        LocalDateTime now = LocalDateTime.now();
        return new Contact(id, name, "", "", "", now, now, "");
    }

    private static Loan withDueDate(Loan loan, LocalDate dueDate) {
        return new Loan(loan.loanId(), loan.direction(), loan.bookId(), loan.bookTitleSnapshot(), loan.contactId(),
                loan.issueDate(), dueDate, loan.returnDate(), loan.status(), loan.baseCharge(), loan.rentCharge(),
                loan.overduePenalty(), loan.damageCharge(), loan.returnCondition(), loan.paymentStatus(),
                loan.pricingSnapshot());
    }

    private static void expectValidation(ThrowingAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected a lending validation failure");
        } catch (LendingValidationException expected) {
            check(!expected.failures().isEmpty());
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Lending service assertion failed");
        }
    }

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }

    private static final class MemoryBooks implements BookRepository {
        private List<Book> records;
        private boolean failNextSave;

        private MemoryBooks(List<Book> records) {
            this.records = new ArrayList<>(records);
        }

        @Override
        public List<Book> findAll() {
            return List.copyOf(records);
        }

        @Override
        public void saveAll(List<Book> books) throws IOException {
            if (failNextSave) {
                failNextSave = false;
                throw new IOException("Expected test write failure");
            }
            records = new ArrayList<>(books);
        }
    }

    private static final class MemoryLoans implements LoanRepository {
        private List<Loan> records;
        private boolean failNextSave;

        private MemoryLoans() {
            this(List.of());
        }

        private MemoryLoans(List<Loan> records) {
            this.records = new ArrayList<>(records);
        }

        @Override
        public List<Loan> findAll() {
            return List.copyOf(records);
        }

        @Override
        public void saveAll(List<Loan> loans) throws IOException {
            if (failNextSave) {
                failNextSave = false;
                throw new IOException("Expected test write failure");
            }
            records = new ArrayList<>(loans);
        }

        private void replace(Loan loan) {
            records = records.stream().map(current -> current.loanId().equals(loan.loanId()) ? loan : current)
                    .toList();
        }
    }

    private static final class MemoryContacts implements ContactRepository {
        private final List<Contact> records;

        private MemoryContacts(List<Contact> records) {
            this.records = List.copyOf(records);
        }

        @Override
        public List<Contact> findAll() {
            return records;
        }

        @Override
        public void saveAll(List<Contact> contacts) {
            throw new UnsupportedOperationException();
        }
    }
}