package services;

import java.util.Objects;
import model.Book;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;

public final class BookNotificationListener implements BookChangeListener {
    private final NotificationService notifications;

    public BookNotificationListener(NotificationService notifications) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
    }

    @Override
    public void onBookChanged(Change change, Book book) {
        NotificationType type = switch (change) {
            case ADDED -> NotificationType.BOOK_ADDED;
            case UPDATED -> NotificationType.BOOK_UPDATED;
            case REMOVED -> NotificationType.BOOK_REMOVED;
        };
        String action = switch (change) {
            case ADDED -> "Added";
            case UPDATED -> "Updated";
            case REMOVED -> "Removed";
        };
        notifications.addEvent(NotificationContext.BOOKKEEPING, type, NotificationSeverity.INFO,
                action + " *" + book.title() + "*.", book.bookId())
                .exceptionally(exception -> {
                    log(exception);
                    return null;
                });
    }

    private static void log(Throwable exception) {
        System.getLogger(BookNotificationListener.class.getName()).log(System.Logger.Level.ERROR,
                "Unable to save book notification", exception);
    }
}