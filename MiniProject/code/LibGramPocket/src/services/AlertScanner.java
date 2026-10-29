package services;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import model.Loan;
import model.LoanDirection;
import model.LoanStatus;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;
import model.PaymentStatus;

public final class AlertScanner {
    public static final int SCAN_INTERVAL_MILLIS = 30 * 60 * 1000;
    private static final DateTimeFormatter DUE_DATE = DateTimeFormatter.ofPattern("dd MMM");
    private final LendingService lending;
    private final ContactService contacts;
    private final SettingsService settings;
    private final NotificationService notifications;

    public AlertScanner(LendingService lending, ContactService contacts, SettingsService settings,
            NotificationService notifications) {
        this.lending = Objects.requireNonNull(lending, "lending");
        this.contacts = Objects.requireNonNull(contacts, "contacts");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.notifications = Objects.requireNonNull(notifications, "notifications");
    }

    /** Scans the requested menu context, doing nothing when alerts are disabled. */
    public void scan(NotificationContext context) throws IOException {
        if (!settings.getBoolean("alerts.enabled")) {
            return;
        }
        LocalDate today = notifications.now().toLocalDate();
        switch (context) {
            case BOOKKEEPING -> scanBookkeeping(today);
            case LEND -> scanLends(today);
            case BORROW -> scanBorrows(today);
        }
    }

    /** Scans all contexts, used at sign-in and by the periodic timer. */
    public void scanAll() throws IOException {
        for (NotificationContext context : NotificationContext.values()) {
            scan(context);
        }
    }

    private void scanBookkeeping(LocalDate today) throws IOException {
        for (Loan loan : lending.activeLends()) {
            add(NotificationContext.BOOKKEEPING, NotificationType.BOOK_LENT_OUT, NotificationSeverity.INFO,
                    "*" + loan.bookTitleSnapshot() + "* is lent to " + contactName(loan.contactId())
                            + ", due " + DUE_DATE.format(loan.dueDate()) + ".",
                    loan.loanId(), today);
            long overdueDays = LoanRules.daysOverdue(loan.dueDate(), today);
            if (overdueDays > 0) {
                add(NotificationContext.BOOKKEEPING, NotificationType.BOOK_OVERDUE_OUT, NotificationSeverity.ALERT,
                        "*" + loan.bookTitleSnapshot() + "* is overdue with " + contactName(loan.contactId()) + ".",
                        loan.loanId(), today);
            }
        }
        for (Loan loan : lending.activeBorrows()) {
            if (LoanRules.isDueSoon(loan, today, settings.getInt("alerts.dueSoonDays"))) {
                long daysUntilDue = ChronoUnit.DAYS.between(today, loan.dueDate());
                add(NotificationContext.BOOKKEEPING, NotificationType.BOOK_BORROWED_DUE,
                        NotificationSeverity.WARNING,
                        "*" + loan.bookTitleSnapshot() + "* is due back to " + contactName(loan.contactId())
                                + " in " + daysUntilDue + " days.",
                        loan.loanId(), today);
            }
        }
    }

    private void scanLends(LocalDate today) throws IOException {
        int dueSoonDays = settings.getInt("alerts.dueSoonDays");
        for (Loan loan : lending.activeLends()) {
            String contact = contactName(loan.contactId());
            long overdueDays = LoanRules.daysOverdue(loan.dueDate(), today);
            if (overdueDays > 0) {
                add(NotificationContext.LEND, NotificationType.LEND_OVERDUE, NotificationSeverity.ALERT,
                        contact + " is overdue with *" + loan.bookTitleSnapshot() + "* (" + overdueDays + " days).",
                        loan.loanId(), today);
            } else if (LoanRules.isDueSoon(loan, today, dueSoonDays)) {
                long daysUntilDue = ChronoUnit.DAYS.between(today, loan.dueDate());
                add(NotificationContext.LEND, NotificationType.LEND_DUE_SOON, NotificationSeverity.WARNING,
                        contact + " is due to return *" + loan.bookTitleSnapshot() + "* in " + daysUntilDue
                                + " days.",
                        loan.loanId(), today);
            }
        }
        addOwedAmounts(today, lending.lendHistory(), NotificationContext.LEND,
                NotificationType.AMOUNT_OWED_TO_YOU, true);
    }

    private void scanBorrows(LocalDate today) throws IOException {
        int dueSoonDays = settings.getInt("alerts.dueSoonDays");
        for (Loan loan : lending.activeBorrows()) {
            String contact = contactName(loan.contactId());
            long overdueDays = LoanRules.daysOverdue(loan.dueDate(), today);
            if (overdueDays > 0) {
                add(NotificationContext.BORROW, NotificationType.BORROW_OVERDUE, NotificationSeverity.ALERT,
                        "Your borrowed *" + loan.bookTitleSnapshot() + "* is overdue by " + overdueDays + " days.",
                        loan.loanId(), today);
            } else if (LoanRules.isDueSoon(loan, today, dueSoonDays)) {
                long daysUntilDue = ChronoUnit.DAYS.between(today, loan.dueDate());
                add(NotificationContext.BORROW, NotificationType.BORROW_DUE_SOON, NotificationSeverity.WARNING,
                        "Your borrowed *" + loan.bookTitleSnapshot() + "* is due back to " + contact + " in "
                                + daysUntilDue + " days.",
                        loan.loanId(), today);
            }
        }
        addOwedAmounts(today, lending.borrowHistory(), NotificationContext.BORROW,
                NotificationType.AMOUNT_YOU_OWE, false);
    }

    private void addOwedAmounts(LocalDate today, List<Loan> history, NotificationContext context,
            NotificationType type, boolean contactOwesOwner) throws IOException {
        Map<String, BigDecimal> amountByContact = history.stream()
                .filter(loan -> loan.paymentStatus() == PaymentStatus.OWED)
                .collect(Collectors.groupingBy(Loan::contactId,
                        Collectors.mapping(AlertScanner::total, Collectors.reducing(BigDecimal.ZERO,
                                BigDecimal::add))));
        for (Map.Entry<String, BigDecimal> entry : amountByContact.entrySet()) {
            String contact = contactName(entry.getKey());
            String amount = money(entry.getValue());
            String message = contactOwesOwner ? contact + " owes you Rs. " + amount + "."
                    : "You owe " + contact + " Rs. " + amount + ".";
            add(context, type, NotificationSeverity.WARNING, message, entry.getKey(), today);
        }
    }

    private void add(NotificationContext context, NotificationType type, NotificationSeverity severity,
            String message, String refId, LocalDate date) throws IOException {
        String key = type.name() + ":" + refId + ":" + date;
        notifications.addIfAbsent(context, type, severity, message, refId, key);
    }

    private String contactName(String contactId) throws IOException {
        return contacts.getById(contactId).map(model.Contact::name).orElse("Unknown contact");
    }

    private static BigDecimal total(Loan loan) {
        return loan.baseCharge()
                .add(loan.rentCharge() == null ? BigDecimal.ZERO : loan.rentCharge())
                .add(loan.overduePenalty() == null ? BigDecimal.ZERO : loan.overduePenalty())
                .add(loan.damageCharge() == null ? BigDecimal.ZERO : loan.damageCharge());
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}