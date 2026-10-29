package data;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Book;
import model.Book.Condition;
import model.Book.OwnershipType;
import model.Book.ReadingStatus;

public final class CsvBookRepository implements BookRepository {
    private static final List<String> HEADER = List.of(
            "bookId", "ownershipType", "lenderContactId", "isbn", "title", "authors", "publisher",
            "publicationYear", "edition", "category", "language", "price", "totalCopies", "availableCopies",
            "condition", "readingStatus", "notes", "dateAdded", "archived", "schemaVersion");

    private final Path file;
    private List<Book> books;

    public CsvBookRepository(Path file) throws IOException {
        this.file = file;
        this.books = loadBooks();
        if (!CsvUtil.hasHeaderColumn(file, "schemaVersion")) {
            writeBooks();
        }
    }

    @Override
    public List<Book> findAll() {
        return List.copyOf(books);
    }

    @Override
    public void saveAll(List<Book> books) throws IOException {
        List<Book> updatedBooks = List.copyOf(books);
        writeBooks(updatedBooks);
        this.books = updatedBooks;
    }

    private List<Book> loadBooks() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.getFirst();
        List<Book> loaded = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> row = records.get(rowIndex);
            try {
                loaded.add(new Book(
                        value(row, header, "bookId"), enumValue(OwnershipType.class,
                                value(row, header, "ownershipType")),
                        value(row, header, "lenderContactId"), value(row, header, "isbn"),
                        value(row, header, "title"), value(row, header, "authors"),
                        value(row, header, "publisher"), value(row, header, "publicationYear"),
                        value(row, header, "edition"), value(row, header, "category"),
                        value(row, header, "language"), decimal(value(row, header, "price")),
                        integer(value(row, header, "totalCopies")), integer(value(row, header, "availableCopies")),
                        enumValue(Condition.class, value(row, header, "condition")),
                        enumValue(ReadingStatus.class, value(row, header, "readingStatus")),
                        value(row, header, "notes"),
                        LocalDate.parse(value(row, header, "dateAdded")),
                        Boolean.parseBoolean(value(row, header, "archived"))));
            } catch (IllegalArgumentException exception) {
                System.getLogger(CsvBookRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed book row " + (rowIndex + 1) + " in " + file, exception);
            }
        }
        return List.copyOf(loaded);
    }

    private void writeBooks() throws IOException {
        writeBooks(books);
    }

    private void writeBooks(List<Book> records) throws IOException {
        List<List<String>> rows = records.stream().map(book -> List.of(
                book.bookId(), displayEnum(book.ownershipType().name()), book.lenderContactId(), book.isbn(),
                book.title(),
                book.authors(), book.publisher(), book.publicationYear(), book.edition(), book.category(),
                book.language(), book.price().toPlainString(), Integer.toString(book.totalCopies()),
                Integer.toString(book.availableCopies()), displayEnum(book.condition().name()),
                displayEnum(book.readingStatus().name()),
                book.notes(), book.dateAdded().toString(), Boolean.toString(book.archived()),
                CsvUtil.SCHEMA_VERSION)).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }

    private static String value(List<String> row, List<String> header, String name) {
        int index = header.indexOf(name);
        return index < 0 || index >= row.size() ? "" : row.get(index);
    }

    private static BigDecimal decimal(String value) {
        return value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
    }

    private static int integer(String value) {
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> enumType, String value) {
        return Enum.valueOf(enumType, value.toUpperCase(Locale.ROOT));
    }

    private static String displayEnum(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }
}