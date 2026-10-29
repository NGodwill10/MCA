package services;

import data.BookRepository;
import data.LoanRepository;
import data.MemberRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import model.Book;
import model.Loan;
import model.LoanStatus;
import model.Member;
import model.MemberStatus;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;

/** Scans current records for daily, de-duplicated alerts. */
public final class AlertScanner implements AutoCloseable {
    public static final int TIMER_INTERVAL_MILLIS = 30 * 60 * 1000;
    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final SettingsService settings;
    private final NotificationService notifications;
    private final Clock clock;
    private final Runnable maintenance;
    private final ExecutorService executor;

    public AlertScanner(BookRepository books, MemberRepository members, LoanRepository loans,
            SettingsService settings, NotificationService notifications) {
        this(books, members, loans, settings, notifications, Clock.systemDefaultZone());
    }

    public AlertScanner(BookRepository books, MemberRepository members, LoanRepository loans,
            SettingsService settings, NotificationService notifications, Clock clock) {
        this(books, members, loans, settings, notifications, clock, () -> { });
    }

    public AlertScanner(BookRepository books, MemberRepository members, LoanRepository loans,
            SettingsService settings, NotificationService notifications, Clock clock, Runnable maintenance) {
        this.books = Objects.requireNonNull(books); this.members = Objects.requireNonNull(members);
        this.loans = Objects.requireNonNull(loans); this.settings = Objects.requireNonNull(settings);
        this.notifications = Objects.requireNonNull(notifications); this.clock = Objects.requireNonNull(clock);
        this.maintenance = Objects.requireNonNull(maintenance);
        executor = Executors.newSingleThreadExecutor(r -> { Thread thread = new Thread(r, "libgram-alert-scanner"); thread.setDaemon(true); return thread; });
    }

    /** Schedules a full scan away from Swing's EDT. */
    public void scanAsync() { executor.execute(() -> { try { scan(); } catch (IOException exception) {
        System.getLogger(AlertScanner.class.getName()).log(System.Logger.Level.WARNING, "Alert scan failed", exception);
    }}); }

    /** Scans every supported alert source synchronously; intended for tests and Scan now. */
    public void scan() throws IOException { scan(null); }

    private void scan(NotificationContext only) throws IOException {
        maintenance.run();
        if (!settings.getBoolean("alerts.enabled")) return;
        LocalDate today = LocalDate.now(clock);
        List<Book> bookRecords = books.findAll();
        List<Member> memberRecords = members.findAll();
        List<Loan> loanRecords = loans.findAll();
        int threshold = settings.getInt("stock.lowThreshold");
        for (Book book : bookRecords) {
            if (only != null && only != NotificationContext.BOOKKEEPING) continue;
            if (book.availableCopies() <= threshold) alert(NotificationContext.BOOKKEEPING, NotificationType.LOW_STOCK,
                    NotificationSeverity.WARNING, "Low stock: *" + book.title() + "* has " + book.availableCopies()
                            + " lendable copies", book.bookId(), today);
        }
        for (Loan loan : loanRecords) {
            if (loan.status() != LoanStatus.ACTIVE || !loan.dueDate().isBefore(today)) continue;
            long days = ChronoUnit.DAYS.between(loan.dueDate(), today);
            Book book = bookRecords.stream().filter(b -> b.bookId().equals(loan.bookId())).findFirst().orElse(null);
            String title = book == null ? loan.bookTitleSnapshot() : book.title();
            if (only == null || only == NotificationContext.BOOKKEEPING) {
                alert(NotificationContext.BOOKKEEPING, NotificationType.BOOK_OVERDUE, NotificationSeverity.ALERT,
                        "Overdue copy: *" + title + "* (" + days + " days)", loan.loanId(), today);
            }
            Member member = memberRecords.stream().filter(m -> m.memberId().equals(loan.memberId())).findFirst().orElse(null);
            String name = member == null ? loan.memberId() : member.name() + " (" + member.memberId() + ")";
            if (only == null || only == NotificationContext.MEMBERS) {
                alert(NotificationContext.MEMBERS, NotificationType.MEMBER_OVERDUE, NotificationSeverity.ALERT,
                        name + " is overdue with *" + title + "* (" + days + " days)", loan.loanId(), today);
            }
        }
        int expiryDays = settings.getInt("alerts.membershipExpiryDays");
        for (Member member : memberRecords) {
            if (member.status() != MemberStatus.ACTIVE || (only != null && only != NotificationContext.MEMBERS)) continue;
            long until = ChronoUnit.DAYS.between(today, member.expiryDate());
            if (until < 0) alert(NotificationContext.MEMBERS, NotificationType.MEMBERSHIP_EXPIRED,
                    NotificationSeverity.ALERT, member.name() + "'s membership has expired", member.memberId(), today);
            else if (until <= expiryDays) alert(NotificationContext.MEMBERS, NotificationType.MEMBERSHIP_EXPIRING,
                    NotificationSeverity.WARNING, member.name() + "'s membership expires in " + until + " days",
                    member.memberId(), today);
        }
    }

    public void scanContext(NotificationContext context) {
        if (context == NotificationContext.BOOKKEEPING || context == NotificationContext.MEMBERS) {
            executor.execute(() -> { try { scan(context); } catch (IOException exception) {
                System.getLogger(AlertScanner.class.getName()).log(System.Logger.Level.WARNING, "Context alert scan failed", exception);
            }});
        }
    }

    private void alert(NotificationContext context, NotificationType type, NotificationSeverity severity,
            String message, String refId, LocalDate date) throws IOException {
        notifications.addAlert(context, type, severity, message, refId, type.name() + ":" + refId + ":" + date, date);
    }

    @Override public void close() { executor.shutdownNow(); }
}
