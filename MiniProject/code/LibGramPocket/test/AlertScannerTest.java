import data.BookRepository;
import data.ContactRepository;
import data.LoanRepository;
import data.NotificationRepository;
import data.SettingsRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import model.ReturnCondition;
import services.AlertScanner;
import services.BookChangeListener;
import services.BookNotificationListener;
import services.ContactService;
import services.LendingService;
import services.LoanEventListener;
import services.LoanNotificationListener;
import services.NotificationService;
import services.SettingsDefaults;
import services.SettingsService;

public final class AlertScannerTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);
    private static final String PRICING = "base=OFF:0.00;rent=OFF:DAY:0.00;penalty=OFF:0.00:0.00";

    private AlertScannerTest() {
    }

    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-21T12:00:00Z"));
        MemoryNotifications notificationRepository = new MemoryNotifications();
        NotificationService notifications = new NotificationService(notificationRepository, clock);
        MemoryContacts contactRepository = new MemoryContacts(List.of(contact("ravi", "Ravi K."),
                contact("meera", "Meera"), contact("sam", "Sam"), contact("jo", "Jo")));
        MemoryLoans loanRepository = new MemoryLoans(testLoans());
        MemoryBooks bookRepository = new MemoryBooks(List.of(
                book("owned", Book.OwnershipType.OWNED, 4, 0, "Owned title"),
                book("borrowed", Book.OwnershipType.BORROWED, 1, 1, "Emma")));
        MemorySettings settingsRepository = new MemorySettings();
        SettingsService settings = new SettingsService(settingsRepository);
        settings.save(Map.of("alerts.dueSoonDays", "3"));
        LendingService lending = new LendingService(bookRepository, loanRepository, contactRepository, settings);
        ContactService contacts = new ContactService(contactRepository, loanRepository);
        AlertScanner scanner = new AlertScanner(lending, contacts, settings, notifications);

        verifyListeners(notifications, contacts);
        scanner.scanAll();
        int afterFirstScan = notificationRepository.findAll().size();
        scanner.scanAll();
        check(notificationRepository.findAll().size() == afterFirstScan);
        verifyAlertRouting(notifications);

        clock.setInstant(Instant.parse("2026-10-22T12:00:00Z"));
        scanner.scanAll();
        check(notificationRepository.findAll().size() > afterFirstScan);
        verifyDisabledScannerStillAllowsEvents(scanner, settings, notificationRepository, notifications);
        verifyAccountIsolation(notificationRepository, clock);
        notifications.close();
        System.out.println("AlertScannerTest passed");
    }

    private static void verifyListeners(NotificationService notifications, ContactService contacts) throws Exception {
        Book sample = book("event-book", Book.OwnershipType.OWNED, 1, 1, "Event book");
        BookNotificationListener bookListener = new BookNotificationListener(notifications);
        bookListener.onBookChanged(BookChangeListener.Change.ADDED, sample);
        bookListener.onBookChanged(BookChangeListener.Change.UPDATED, sample);
        bookListener.onBookChanged(BookChangeListener.Change.REMOVED, sample);

        LoanNotificationListener loanListener = new LoanNotificationListener(notifications, contacts);
        Loan lent = loan("event-lend", LoanDirection.LENT, "owned", "Dune", "ravi", TODAY.plusDays(4),
                LoanStatus.ACTIVE, PaymentStatus.NONE, BigDecimal.ZERO);
        Loan borrowed = loan("event-borrow", LoanDirection.BORROWED, "borrowed", "Emma", "meera",
                TODAY.plusDays(2), LoanStatus.ACTIVE, PaymentStatus.NONE, BigDecimal.ZERO);
        loanListener.onLoanChanged(lent, LoanEventListener.Change.LENT);
        loanListener.onLoanChanged(borrowed, LoanEventListener.Change.BORROWED);
        loanListener.onLoanChanged(lent, LoanEventListener.Change.RETURNED);
        loanListener.onLoanChanged(borrowed, LoanEventListener.Change.RETURNED);
        loanListener.onLoanChanged(lent, LoanEventListener.Change.PAYMENT_UPDATED);
        notifications.flushEventWrites().join();

        List<Notification> bookkeeping = notifications.listByContext(NotificationContext.BOOKKEEPING, 0, 50);
        check(containsType(bookkeeping, NotificationType.BOOK_ADDED, NotificationSeverity.INFO));
        check(containsType(bookkeeping, NotificationType.BOOK_UPDATED, NotificationSeverity.INFO));
        check(containsType(bookkeeping, NotificationType.BOOK_REMOVED, NotificationSeverity.INFO));
        List<Notification> lends = notifications.listByContext(NotificationContext.LEND, 0, 50);
        check(containsType(lends, NotificationType.BOOK_LENT, NotificationSeverity.INFO));
        check(containsType(lends, NotificationType.BOOK_RETURNED, NotificationSeverity.INFO));
        check(containsType(lends, NotificationType.PAYMENT_MARKED_PAID, NotificationSeverity.INFO));
        List<Notification> borrows = notifications.listByContext(NotificationContext.BORROW, 0, 50);
        check(containsType(borrows, NotificationType.BOOK_BORROWED, NotificationSeverity.INFO));
        check(containsType(borrows, NotificationType.BORROW_RETURNED, NotificationSeverity.INFO));
    }

    private static void verifyAlertRouting(NotificationService notifications) throws IOException {
        List<Notification> bookkeeping = notifications.listByContext(NotificationContext.BOOKKEEPING, 0, 100);
        check(countType(bookkeeping, NotificationType.BOOK_LENT_OUT) == 4);
        check(countType(bookkeeping, NotificationType.BOOK_OVERDUE_OUT) == 1);
        check(countType(bookkeeping, NotificationType.BOOK_BORROWED_DUE) == 1);
        check(bookkeeping.stream().filter(item -> item.type() == NotificationType.BOOK_OVERDUE_OUT)
                .anyMatch(item -> item.message().contains("Dune") && item.message().contains("Ravi K.")));

        List<Notification> lends = notifications.listByContext(NotificationContext.LEND, 0, 100);
        check(countType(lends, NotificationType.LEND_OVERDUE) == 1);
        check(countType(lends, NotificationType.LEND_DUE_SOON) == 2);
        check(countType(lends, NotificationType.AMOUNT_OWED_TO_YOU) == 1);
        check(lends.stream().filter(item -> item.type() == NotificationType.LEND_DUE_SOON)
                .noneMatch(item -> item.refId().equals("lend-outside")));
        check(lends.stream().filter(item -> item.type() == NotificationType.AMOUNT_OWED_TO_YOU)
            .anyMatch(item -> item.message().startsWith("Ravi K. owes you Rs. 28.00")));

        List<Notification> borrows = notifications.listByContext(NotificationContext.BORROW, 0, 100);
        check(countType(borrows, NotificationType.BORROW_DUE_SOON) == 1);
        check(countType(borrows, NotificationType.AMOUNT_YOU_OWE) == 1);
        check(borrows.stream().filter(item -> item.type() == NotificationType.BORROW_DUE_SOON)
                .anyMatch(item -> item.message().contains("Emma") && item.message().contains("2 days")));
        check(borrows.stream().filter(item -> item.type() == NotificationType.AMOUNT_YOU_OWE)
            .anyMatch(item -> item.message().startsWith("You owe Meera Rs. 25.00")));
    }

    private static void verifyDisabledScannerStillAllowsEvents(AlertScanner scanner, SettingsService settings,
            MemoryNotifications repository, NotificationService notifications) throws Exception {
        settings.save(Map.of("alerts.enabled", "false"));
        int before = repository.findAll().size();
        scanner.scanAll();
        check(repository.findAll().size() == before);
        new BookNotificationListener(notifications).onBookChanged(BookChangeListener.Change.ADDED,
                book("alerts-off-event", Book.OwnershipType.OWNED, 1, 1, "Event while alerts off"));
        notifications.flushEventWrites().join();
        check(repository.findAll().size() == before + 1);
    }

    private static void verifyAccountIsolation(MemoryNotifications source, Clock clock) throws IOException {
        NotificationService otherAccount = new NotificationService(new MemoryNotifications(), clock);
        check(otherAccount.listByContext(NotificationContext.LEND, 0, 100).isEmpty());
        check(!source.findAll().isEmpty());
        otherAccount.close();
    }

    private static List<Loan> testLoans() {
        return List.of(
                loan("lend-overdue", LoanDirection.LENT, "owned", "Dune", "ravi", TODAY.minusDays(1),
                        LoanStatus.ACTIVE, PaymentStatus.NONE, BigDecimal.ZERO),
                loan("lend-window", LoanDirection.LENT, "owned", "Foundation", "sam", TODAY.plusDays(3),
                        LoanStatus.ACTIVE, PaymentStatus.NONE, BigDecimal.ZERO),
                loan("lend-today", LoanDirection.LENT, "owned", "Solaris", "jo", TODAY,
                        LoanStatus.ACTIVE, PaymentStatus.NONE, BigDecimal.ZERO),
                loan("lend-outside", LoanDirection.LENT, "owned", "Distant book", "jo", TODAY.plusDays(4),
                        LoanStatus.ACTIVE, PaymentStatus.NONE, BigDecimal.ZERO),
                loan("borrow-window", LoanDirection.BORROWED, "borrowed", "Emma", "meera", TODAY.plusDays(2),
                        LoanStatus.ACTIVE, PaymentStatus.NONE, BigDecimal.ZERO),
                loan("lend-owed", LoanDirection.LENT, "owned", "Owed title", "ravi", TODAY.minusDays(2),
                        LoanStatus.RETURNED, PaymentStatus.OWED, new BigDecimal("28.00")),
                loan("borrow-owed", LoanDirection.BORROWED, "borrowed", "Owed borrow", "meera",
                        TODAY.minusDays(2), LoanStatus.RETURNED, PaymentStatus.OWED, new BigDecimal("25.00")));
    }

    private static Loan loan(String id, LoanDirection direction, String bookId, String title, String contactId,
            LocalDate dueDate, LoanStatus status, PaymentStatus payment, BigDecimal total) {
        boolean returned = status == LoanStatus.RETURNED;
        BigDecimal base = total;
        return new Loan(id, direction, bookId, title, contactId, TODAY.minusDays(7), dueDate,
                returned ? TODAY : null, status, base, returned ? BigDecimal.ZERO : null,
                returned ? BigDecimal.ZERO : null, returned ? BigDecimal.ZERO : null,
                returned ? ReturnCondition.GOOD : null, payment, PRICING);
    }

    private static Book book(String id, Book.OwnershipType ownership, int total, int available, String title) {
        return new Book(id, ownership, ownership == Book.OwnershipType.BORROWED ? "meera" : "", "", title,
                "Author", "", "", "", "", "", BigDecimal.ZERO, total, available, Book.Condition.GOOD,
                Book.ReadingStatus.UNREAD, "", TODAY.minusDays(10), false);
    }

    private static Contact contact(String id, String name) {
        LocalDateTime time = LocalDateTime.of(TODAY, java.time.LocalTime.NOON);
        return new Contact(id, name, "", "", "", time, time, "");
    }

    private static boolean containsType(List<Notification> records, NotificationType type,
            NotificationSeverity severity) {
        return records.stream().anyMatch(item -> item.type() == type && item.severity() == severity);
    }

    private static long countType(List<Notification> records, NotificationType type) {
        return records.stream().filter(item -> item.type() == type).count();
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Alert scanner assertion failed");
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void setInstant(Instant value) {
            instant = value;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class MemoryNotifications implements NotificationRepository {
        private List<Notification> records = List.of();

        @Override
        public List<Notification> findAll() {
            return List.copyOf(records);
        }

        @Override
        public void saveAll(List<Notification> notifications) {
            records = List.copyOf(notifications);
        }
    }

    private static final class MemoryBooks implements BookRepository {
        private final List<Book> records;

        private MemoryBooks(List<Book> records) {
            this.records = List.copyOf(records);
        }

        @Override
        public List<Book> findAll() {
            return records;
        }

        @Override
        public void saveAll(List<Book> books) {
            throw new UnsupportedOperationException();
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

    private static final class MemoryLoans implements LoanRepository {
        private final List<Loan> records;

        private MemoryLoans(List<Loan> records) {
            this.records = List.copyOf(records);
        }

        @Override
        public List<Loan> findAll() {
            return records;
        }

        @Override
        public void saveAll(List<Loan> loans) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class MemorySettings implements SettingsRepository {
        private Map<String, String> values = SettingsDefaults.values();

        @Override
        public Map<String, String> load() {
            return values;
        }

        @Override
        public void save(Map<String, String> settings) {
            values = Map.copyOf(settings);
        }
    }
}