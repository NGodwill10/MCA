package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Loan(
        String loanId,
        LoanDirection direction,
        String bookId,
        String bookTitleSnapshot,
        String contactId,
        LocalDate issueDate,
        LocalDate dueDate,
        LocalDate returnDate,
        LoanStatus status,
        BigDecimal baseCharge,
        BigDecimal rentCharge,
        BigDecimal overduePenalty,
        BigDecimal damageCharge,
        ReturnCondition returnCondition,
        PaymentStatus paymentStatus,
        String pricingSnapshot) {
}