package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Book(
        String bookId,
        OwnershipType ownershipType,
        String lenderContactId,
        String isbn,
        String title,
        String authors,
        String publisher,
        String publicationYear,
        String edition,
        String category,
        String language,
        BigDecimal price,
        int totalCopies,
        int availableCopies,
        Condition condition,
        ReadingStatus readingStatus,
        String notes,
        LocalDate dateAdded,
        boolean archived) {

    public enum OwnershipType {
        OWNED,
        BORROWED
    }

    public enum Condition {
        NEW,
        GOOD,
        WORN,
        DAMAGED
    }

    public enum ReadingStatus {
        UNREAD,
        READING,
        READ
    }
}