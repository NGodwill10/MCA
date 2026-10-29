package services;

import model.Book;

@FunctionalInterface
public interface BookChangeListener {
    BookChangeListener NO_OP = (change, book) -> {
    };

    void onBookChanged(Change change, Book book);

    enum Change {
        ADDED,
        UPDATED,
        REMOVED
    }
}