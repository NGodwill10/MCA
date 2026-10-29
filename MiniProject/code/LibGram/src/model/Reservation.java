package model;

import java.time.LocalDateTime;

public record Reservation(String reservationId, String bookId, String bookTitleSnapshot, String memberId,
        LocalDateTime reservedAt, ReservationStatus status, LocalDateTime readyAt, LocalDateTime expiresAt,
        String feeChargeId) { }
