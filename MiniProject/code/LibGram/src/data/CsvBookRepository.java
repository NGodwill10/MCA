package data;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import model.Book;

public final class CsvBookRepository implements BookRepository {
    private static final List<String> HEADER = List.of(
            "bookId", "isbn", "title", "authors", "publisher", "publicationYear", "edition", "category",
            "language", "shelfLocation", "price", "totalCopies", "availableCopies", "dateAdded");

    private final Path file;
    private List<Book> books;

    public CsvBookRepository(Path file) throws IOException {
        this.file = file;
        this.books = loadBooks();
        if (!Files.exists(file) || Files.size(file) == 0) {
            writeBooks(books);
        }
    }

    @Override
    public List<Book> findAll() throws IOException {
        return List.copyOf(books);
    }

    private List<Book> loadBooks() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.getFirst();
        List<Book> books = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> row = records.get(rowIndex);
            try {
                books.add(new Book(
                        value(row, header, "bookId"), value(row, header, "isbn"), value(row, header, "title"),
                        value(row, header, "authors"), value(row, header, "publisher"),
                        value(row, header, "publicationYear"), value(row, header, "edition"),
                        value(row, header, "category"), value(row, header, "language"),
                        value(row, header, "shelfLocation"), decimal(value(row, header, "price")),
                        integer(value(row, header, "totalCopies")), integer(value(row, header, "availableCopies")),
                        LocalDate.parse(value(row, header, "dateAdded"))));
            } catch (IllegalArgumentException | DateTimeException exception) {
                System.getLogger(CsvBookRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed book row " + (rowIndex + 1) + " in " + file, exception);
            }
        }
        return List.copyOf(books);
    }

    @Override
    public void saveAll(List<Book> books) throws IOException {
        List<Book> updatedBooks = List.copyOf(books);
        writeBooks(updatedBooks);
        this.books = updatedBooks;
    }

    private void writeBooks(List<Book> books) throws IOException {
        List<List<String>> rows = books.stream().map(book -> List.of(
                book.bookId(), book.isbn(), book.title(), book.authors(), book.publisher(), book.publicationYear(),
                book.edition(), book.category(), book.language(), book.shelfLocation(), book.price().toPlainString(),
                Integer.toString(book.totalCopies()), Integer.toString(book.availableCopies()),
                book.dateAdded().toString())).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }

    private static String value(List<String> row, List<String> header, String name) {
        int index = header.indexOf(name);
        if (index < 0 || index >= row.size()) {
            return "";
        }
        return row.get(index);
    }

    private static BigDecimal decimal(String value) {
        return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private static int integer(String value) {
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }
}