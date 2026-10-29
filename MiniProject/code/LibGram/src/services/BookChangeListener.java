package services;

import model.Book;

@FunctionalInterface
public interface BookChangeListener {
    enum Change { ADDED, UPDATED, REMOVED }

    void onBookChanged(Change change, Book book);

    BookChangeListener NO_OP = (change, book) -> { };
}
