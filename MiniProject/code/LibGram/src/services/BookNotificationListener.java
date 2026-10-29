package services;

import java.io.IOException;
import model.Book;
import model.NotificationContext;
import model.NotificationType;

public final class BookNotificationListener implements BookChangeListener {
    private final NotificationService notifications;
    public BookNotificationListener(NotificationService notifications) { this.notifications = notifications; }
    @Override public void onBookChanged(Change change, Book book) {
        try {
            NotificationType type = switch (change) {
                case ADDED -> NotificationType.BOOK_ADDED; case UPDATED -> NotificationType.BOOK_UPDATED;
                case REMOVED -> NotificationType.BOOK_REMOVED;
            };
            notifications.addEvent(NotificationContext.BOOKKEEPING, type,
                    "Book " + change.name().toLowerCase() + ": " + book.title(), book.bookId());
        } catch (IOException exception) { throw new IllegalStateException("Could not write book notification.", exception); }
    }
}
