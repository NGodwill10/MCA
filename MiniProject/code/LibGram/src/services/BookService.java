package services;

import data.BookRepository;
import java.io.IOException;

/** Phase 2 name for the catalog service; kept as a small compatibility facade. */
public final class BookService extends CatalogService {
    public BookService(BookRepository books) {
        super(books);
    }

    public BookService(BookRepository books, BookChangeListener listener) {
        super(books, listener);
    }
}
