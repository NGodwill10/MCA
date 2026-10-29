package data;

import java.io.IOException;
import java.util.List;
import model.Book;

public interface BookRepository {
    List<Book> findAll() throws IOException;

    void saveAll(List<Book> books) throws IOException;
}