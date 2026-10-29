package services;

import java.io.IOException;
import java.math.BigDecimal;
import model.Loan;
import model.NotificationContext;
import model.NotificationType;

public final class ReturnNotificationListener implements ReturnEventListener {
    private final NotificationService notifications;
    public ReturnNotificationListener(NotificationService notifications) { this.notifications = notifications; }
    @Override public void onReturned(Loan loan, boolean damaged, BigDecimal overdueFine) {
        try {
            notifications.addEvent(NotificationContext.RECOVERY, NotificationType.BOOK_RETURNED,
                    "Returned *" + loan.bookTitleSnapshot() + "* from " + loan.memberId(), loan.loanId());
            if (damaged) notifications.addEvent(NotificationContext.RECOVERY, NotificationType.BOOK_DAMAGED,
                    "*" + loan.bookTitleSnapshot() + "* was returned damaged", loan.loanId());
            if (overdueFine.signum() > 0) notifications.addEvent(NotificationContext.RECOVERY, NotificationType.FINE_RAISED,
                    "Fine of Rs. " + overdueFine.setScale(2) + " was raised for *" + loan.bookTitleSnapshot() + "*",
                    loan.loanId());
        } catch (IOException exception) { throw new IllegalStateException("Could not write return notification.", exception); }
    }
}
