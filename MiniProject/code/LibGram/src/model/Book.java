package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Book(
        String bookId,
        String isbn,
        String title,
        String authors,
        String publisher,
        String publicationYear,
        String edition,
        String category,
        String language,
        String shelfLocation,
        BigDecimal price,
        int totalCopies,
        int availableCopies,
        LocalDate dateAdded) {
}