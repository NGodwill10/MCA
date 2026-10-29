package services;

import model.Book;

public final class DuplicateIsbnException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final transient Book existingBook;

    public DuplicateIsbnException(Book existingBook) {
        super("That ISBN already belongs to \"" + existingBook.title() + "\".");
        this.existingBook = existingBook;
    }

    public Book existingBook() {
        return existingBook;
    }
}