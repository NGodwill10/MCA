import data.CsvBookRepository;
import data.CsvContactRepository;
import data.CsvLoanRepository;
import data.CsvNotificationRepository;
import data.CsvSettingsRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import model.Book;
import model.Contact;
import model.Loan;
import model.LoanDirection;
import model.LoanStatus;
import model.Notification;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;
import model.PaymentStatus;
import model.PricingTerms;
import model.ReturnCondition;
import services.BookService;
import services.ChargeCalculator;
import services.ContactService;
import services.LendingService;
import services.SettingsService;

public final class PhaseEightInvariantTest {
    private static final PricingTerms FREE = new PricingTerms(false, BigDecimal.ZERO, false,
            PricingTerms.RentUnit.DAY, BigDecimal.ZERO, false, BigDecimal.ZERO, BigDecimal.ZERO);

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("libgrampocket-invariant-test-");
        try {
            verifyAccount(root.resolve("account-a"), "A");
            verifyAccount(root.resolve("account-b"), "B");
            System.out.println("PhaseEightInvariantTest passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyAccount(Path folder, String accountName) throws Exception {
        Files.createDirectories(folder);
        CsvBookRepository booksRepository = new CsvBookRepository(folder.resolve("books.txt"));
        CsvContactRepository contactsRepository = new CsvContactRepository(folder.resolve("contacts.txt"));
        CsvLoanRepository loansRepository = new CsvLoanRepository(folder.resolve("loans.txt"));
        SettingsService settings = new SettingsService(new CsvSettingsRepository(folder.resolve("settings.txt")));
        Contact contact = new Contact("contact", "Fictional " + accountName, "", "", "",
                LocalDateTime.now(), LocalDateTime.now(), "");
        contactsRepository.saveAll(List.of(contact));
        ContactService contactService = new ContactService(contactsRepository, loansRepository);
        BookService bookService = new BookService(booksRepository, loansRepository, contactsRepository);
        LendingService lending = new LendingService(booksRepository, loansRepository, contactsRepository, settings);
        Random random = new Random(82731);
        List<Integer> operations = new ArrayList<>(6);

        for (int step = 0; step < 60; step++) {
            int operation = random.nextInt(6);
            switch (operation) {
                case 0 -> addOwned(bookService, accountName, step);
                case 1 -> lendAvailable(bookService, lending, contact, random);
                case 2 -> returnActive(lending, random);
                case 3 -> borrowNew(bookService, lending, contact, accountName, step);
                case 4 -> updateOwned(bookService, booksRepository, step);
                case 5 -> removeAvailable(bookService, booksRepository, loansRepository, random);
                default -> throw new AssertionError("Unexpected randomized operation");
            }
            operations.add(operation);
            checkInvariants(booksRepository, loansRepository);
        }

        for (int operation : List.of(0, 1, 2, 3, 4, 5)) {
            check(operations.contains(operation));
        }
        settings.save(java.util.Map.of("loan.defaultDays", accountName.equals("A") ? "10" : "20"));
        CsvNotificationRepository notifications = new CsvNotificationRepository(folder.resolve("notifications.txt"));
        notifications.saveAll(List.of(new Notification(UUID.randomUUID().toString(), LocalDateTime.now(),
                NotificationContext.BOOKKEEPING, NotificationType.BOOK_ADDED, NotificationSeverity.INFO,
                "Account " + accountName, "", "", false)));
        check(booksRepository.findAll().stream().allMatch(book -> book.title().startsWith(accountName)
                || book.title().startsWith("Borrowed " + accountName)));
        check(contactsRepository.findAll().getFirst().name().equals("Fictional " + accountName));
        check(settings.getInt("loan.defaultDays") == (accountName.equals("A") ? 10 : 20));
        check(notifications.findAll().getFirst().message().equals("Account " + accountName));
        check(!loansRepository.findAll().isEmpty());
        check(contactService.listSorted().size() == 1);
    }

    private static void addOwned(BookService service, String accountName, int step) throws Exception {
        service.add(draft(accountName + " Book " + step, 1 + step % 2));
    }

    private static void lendAvailable(BookService books, LendingService lending, Contact contact, Random random)
            throws Exception {
        List<Book> available = books.listSorted().stream()
                .filter(book -> book.ownershipType() == Book.OwnershipType.OWNED && book.availableCopies() > 0)
                .toList();
        if (available.isEmpty()) {
            return;
        }
        Book selected = available.get(random.nextInt(available.size()));
        try {
            lending.lend(contact.contactId(), List.of(new LendingService.LendRequest(selected.bookId(),
                    LocalDate.now().plusDays(5))), FREE);
        } catch (services.LendingValidationException expected) {
            check(!expected.failures().isEmpty());
        }
    }

    private static void returnActive(LendingService lending, Random random) throws Exception {
        List<Loan> active = new ArrayList<>(lending.activeLends());
        active.addAll(lending.activeBorrows());
        if (!active.isEmpty()) {
            Loan selected = active.get(random.nextInt(active.size()));
            lending.returnLoan(selected.loanId(), LocalDate.now(), ReturnCondition.GOOD, BigDecimal.ZERO, false);
        }
    }

    private static void borrowNew(BookService books, LendingService lending, Contact contact, String accountName,
            int step) throws Exception {
        Book borrowed = books.prepareBorrowedBook(draft("Borrowed " + accountName + " " + step, 1),
                contact.contactId());
        lending.borrowNewBook(borrowed, LocalDate.now().plusDays(8), FREE);
    }

    private static void updateOwned(BookService service, CsvBookRepository repository, int step) throws Exception {
        Book existing = repository.findAll().stream()
                .filter(book -> book.ownershipType() == Book.OwnershipType.OWNED).findFirst().orElse(null);
        if (existing != null) {
            service.update(new Book(existing.bookId(), existing.ownershipType(), existing.lenderContactId(),
                    existing.isbn(), existing.title() + " Updated " + step, existing.authors(), existing.publisher(),
                    existing.publicationYear(), existing.edition(), existing.category(), existing.language(),
                    existing.price(), existing.totalCopies(), existing.availableCopies(), existing.condition(),
                    existing.readingStatus(), existing.notes(), existing.dateAdded(), existing.archived()));
        }
    }

    private static void removeAvailable(BookService service, CsvBookRepository books, CsvLoanRepository loans,
            Random random) throws Exception {
        List<Book> removable = books.findAll().stream()
                .filter(book -> book.ownershipType() == Book.OwnershipType.OWNED)
                .filter(book -> loans.findAll().stream().noneMatch(loan -> loan.direction() == LoanDirection.LENT
                        && loan.status() == LoanStatus.ACTIVE && loan.bookId().equals(book.bookId())))
                .toList();
        if (!removable.isEmpty()) {
            service.remove(removable.get(random.nextInt(removable.size())).bookId());
        }
    }

    private static void checkInvariants(CsvBookRepository books, CsvLoanRepository loans) throws Exception {
        List<Loan> records = loans.findAll();
        List<Book> catalog = books.findAll();
        for (Book book : catalog) {
            if (book.ownershipType() == Book.OwnershipType.OWNED) {
                long active = records.stream().filter(loan -> loan.direction() == LoanDirection.LENT
                        && loan.status() == LoanStatus.ACTIVE && loan.bookId().equals(book.bookId())).count();
                check(book.availableCopies() == book.totalCopies() - active);
            }
        }
        for (Loan loan : records) {
            if (loan.direction() == LoanDirection.LENT) {
                check(catalog.stream().filter(book -> book.bookId().equals(loan.bookId()))
                        .noneMatch(book -> book.ownershipType() == Book.OwnershipType.BORROWED));
            }
            if (loan.status() == LoanStatus.RETURNED) {
                BigDecimal damage = loan.damageCharge() == null ? BigDecimal.ZERO : loan.damageCharge();
                BigDecimal expected = ChargeCalculator.calculateReturn(PricingTerms.parse(loan.pricingSnapshot()),
                        loan.issueDate(), loan.dueDate(), loan.returnDate(), loan.returnCondition(), damage).total();
                BigDecimal actual = loan.baseCharge().add(value(loan.rentCharge()))
                        .add(value(loan.overduePenalty())).add(value(loan.damageCharge()));
                check(expected.equals(actual));
            }
        }
    }

    private static BigDecimal value(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private static Book draft(String title, int copies) {
        return new Book("", Book.OwnershipType.OWNED, "", "", title, "A. Example", "", "", "", "", "",
                BigDecimal.ZERO, copies, copies, Book.Condition.GOOD, Book.ReadingStatus.UNREAD, "", LocalDate.now(),
                false);
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Randomized invariant or account-isolation assertion failed");
        }
    }
}