package services;

import java.io.IOException;
import model.Loan;
import model.NotificationContext;
import model.NotificationType;

public final class LostBookNotificationListener implements LostBookEventListener {
    private final NotificationService notifications;
    public LostBookNotificationListener(NotificationService notifications) { this.notifications = notifications; }
    @Override public void onBookLost(Loan loan) {
        try { notifications.addEvent(NotificationContext.RECOVERY, NotificationType.BOOK_LOST,
                "Book marked lost: " + loan.bookTitleSnapshot() + " for member " + loan.memberId(), loan.loanId()); }
        catch (IOException exception) { throw new IllegalStateException("Could not write lost-book notification.", exception); }
    }
}
