import data.BookRepository;
import data.LoanRepository;
import data.MemberRepository;
import data.NotificationRepository;
import data.SettingsRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import model.Book;
import model.Loan;
import model.LoanStatus;
import model.Member;
import model.MemberStatus;
import model.MembershipType;
import model.Notification;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;
import services.AlertScanner;
import services.NotificationService;
import services.SettingsDefaults;
import services.SettingsService;

/** Plain-JDK Phase 8 checks; run with java -cp out PhaseEightNotificationTest. */
public final class PhaseEightNotificationTest {
    public static void main(String[] args) throws Exception {
        pagingAndDismissal(); scannerDeduplicatesAndHonoursSettings();
        System.out.println("PhaseEightNotificationTest passed");
    }

    private static void pagingAndDismissal() throws Exception {
        MemoryNotifications store = new MemoryNotifications(); NotificationService service = new NotificationService(store);
        for (int i = 0; i < 201; i++) service.addEvent(NotificationContext.MEMBERS, NotificationType.MEMBER_UPDATED, "m" + i, "m");
        check(service.list(NotificationContext.MEMBERS, 0, 200).size() == 200, "first page");
        check(service.list(NotificationContext.MEMBERS, 1, 200).size() == 1, "older page");
        service.dismissAll(NotificationContext.MEMBERS);
        check(service.list(NotificationContext.MEMBERS).isEmpty() && store.values.size() == 201, "dismiss keeps rows");
    }

    private static void scannerDeduplicatesAndHonoursSettings() throws Exception {
        LocalDate today = LocalDate.of(2026, 10, 21);
        Book book = new Book("BK-1", "", "Dune", "Frank Herbert", "", "", "", "", "", "",
                new BigDecimal("20.00"), 2, 0, today);
        Member member = new Member("MEM-1", "Asha Rao", "", "a@b.com", "", MembershipType.INDIVIDUAL,
                today.minusYears(1), today.plusDays(14), MemberStatus.ACTIVE, today.atStartOfDay(), today.atStartOfDay());
        Loan loan = new Loan("LN-1", book.bookId(), book.title(), member.memberId(), today.minusDays(20),
                today.minusDays(1), null, LoanStatus.ACTIVE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "", "", "", "");
        MemoryNotifications store = new MemoryNotifications(); NotificationService service = new NotificationService(store);
        SettingsService settings = new SettingsService(new MemorySettings());
        AlertScanner scanner = new AlertScanner(new Books(book), new Members(member), new Loans(loan), settings, service,
                Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneId.systemDefault()));
        scanner.scan(); scanner.scan();
        check(store.values.stream().filter(n -> n.type() == NotificationType.MEMBER_OVERDUE).count() == 1, "dedup overdue");
        check(store.values.stream().anyMatch(n -> n.type() == NotificationType.MEMBERSHIP_EXPIRING
                && n.severity() == NotificationSeverity.WARNING), "expiry boundary");
        Map<String, String> off = new LinkedHashMap<>(settings.snapshot()); off.put("alerts.enabled", "false");
        settings = new SettingsService(new MemorySettings(off));
        MemoryNotifications disabled = new MemoryNotifications();
        new AlertScanner(new Books(book), new Members(member), new Loans(loan), settings, new NotificationService(disabled), scannerClock()).scan();
        check(disabled.values.isEmpty(), "disabled scanner"); scanner.close();
    }

    private static Clock scannerClock() { return Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneId.systemDefault()); }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); }

    private static final class MemoryNotifications implements NotificationRepository {
        private List<Notification> values = new ArrayList<>();
        public List<Notification> findAll() { return List.copyOf(values); }
        public void saveAll(List<Notification> values) { this.values = new ArrayList<>(values); }
    }
    private static final class MemorySettings implements SettingsRepository {
        private final Map<String, String> values;
        MemorySettings() { this(SettingsDefaults.values()); }
        MemorySettings(Map<String, String> values) { this.values = new LinkedHashMap<>(values); }
        public Map<String, String> load() { return values; }
        public void save(Map<String, String> values) { this.values.clear(); this.values.putAll(values); }
    }
    private record Books(Book value) implements BookRepository { public List<Book> findAll() { return List.of(value); } public void saveAll(List<Book> ignored) { } }
    private record Members(Member value) implements MemberRepository { public List<Member> findAll() { return List.of(value); } public void saveAll(List<Member> ignored) { } }
    private record Loans(Loan value) implements LoanRepository { public List<Loan> findAll() { return List.of(value); } public void saveAll(List<Loan> ignored) { } }
}
