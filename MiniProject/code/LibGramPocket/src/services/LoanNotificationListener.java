package services;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import model.Loan;
import model.LoanDirection;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;

public final class LoanNotificationListener implements LoanEventListener {
    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("dd MMM uuuu HH:mm");
    private final NotificationService notifications;
    private final ContactService contacts;

    public LoanNotificationListener(NotificationService notifications, ContactService contacts) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.contacts = Objects.requireNonNull(contacts, "contacts");
    }

    @Override
    public void onLoanChanged(Loan loan, Change change) {
        NotificationContext context = loan.direction() == LoanDirection.LENT
                ? NotificationContext.LEND : NotificationContext.BORROW;
        NotificationType type = eventType(loan.direction(), change);
        String contactName = contactName(loan.contactId());
        String message = eventMessage(loan, change, contactName);
        notifications.addEvent(context, type, NotificationSeverity.INFO, message, loan.loanId())
            .exceptionally(exception -> {
                System.getLogger(LoanNotificationListener.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to save loan notification", exception);
                return null;
            });
    }

    private NotificationType eventType(LoanDirection direction, Change change) {
        return switch (change) {
            case LENT -> NotificationType.BOOK_LENT;
            case BORROWED -> NotificationType.BOOK_BORROWED;
            case RETURNED -> direction == LoanDirection.LENT
                    ? NotificationType.BOOK_RETURNED : NotificationType.BORROW_RETURNED;
            case PAYMENT_UPDATED -> NotificationType.PAYMENT_MARKED_PAID;
        };
    }

    private String eventMessage(Loan loan, Change change, String contactName) {
        String title = "*" + loan.bookTitleSnapshot() + "*";
        String timestamp = EVENT_TIME.format(notifications.now());
        return switch (change) {
            case LENT -> "Lent " + title + " to " + contactName + " on " + timestamp + ".";
            case BORROWED -> "Borrowed " + title + " from " + contactName + " on " + timestamp + ".";
            case RETURNED -> loan.direction() == LoanDirection.LENT
                    ? "Received " + title + " back from " + contactName + " on " + timestamp + "."
                    : "Returned " + title + " to " + contactName + " on " + timestamp + ".";
            case PAYMENT_UPDATED -> "Marked payment for " + title + " with " + contactName + " as paid.";
        };
    }

    private String contactName(String contactId) {
        try {
            return contacts.getById(contactId).map(model.Contact::name).orElse("Unknown contact");
        } catch (IOException exception) {
            System.getLogger(LoanNotificationListener.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to load contact for loan notification", exception);
            return "Unknown contact";
        }
    }
}