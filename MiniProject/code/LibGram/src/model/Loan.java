package model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Loan(
        String loanId,
        String bookId,
        String bookTitleSnapshot,
        String memberId,
        LocalDate issueDate,
        LocalDate dueDate,
        LocalDate returnDate,
        LoanStatus status,
        BigDecimal baseCharge,
        BigDecimal rentCharge,
        BigDecimal overdueFine,
        String returnCondition,
        String issuedBy,
        String receivedBy,
        String pricingSnapshot) {
}
