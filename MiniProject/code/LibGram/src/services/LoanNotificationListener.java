package services;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import model.Loan;
import model.NotificationContext;
import model.NotificationType;

public final class LoanNotificationListener implements LoanEventListener {
    private final NotificationService notifications;
    public LoanNotificationListener(NotificationService notifications) { this.notifications = notifications; }
    @Override public void onLoanCreated(Loan loan) {
        try { notifications.addEvent(NotificationContext.LENDING, NotificationType.BOOK_LENT,
                "Lent *" + loan.bookTitleSnapshot() + "* to " + loan.memberId() + ", due "
                        + loan.dueDate() + " at " + loan.issueDate().format(DateTimeFormatter.ISO_DATE), loan.loanId());
        } catch (IOException exception) { throw new IllegalStateException("Could not write loan notification.", exception); }
    }
    @Override public void onLendingBlocked(String memberId, String reason) {
        try { notifications.addEvent(NotificationContext.LENDING, NotificationType.LENDING_BLOCKED,
                "Lending blocked for member " + memberId + ": " + reason, memberId); }
        catch (IOException exception) { throw new IllegalStateException("Could not write lending notification.", exception); }
    }
}
