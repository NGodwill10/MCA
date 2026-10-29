package services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import model.Loan;
import model.LoanStatus;

public final class LoanRules {
    private LoanRules() {
    }

    /** Returns the nonnegative whole days between a due date and a later date. */
    /** Returns the nonnegative whole days between a due date and a later date. */
    public static long daysOverdue(LocalDate dueDate, LocalDate date) {
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(date, "date");
        return Math.max(0, ChronoUnit.DAYS.between(dueDate, date));
    }

    /** Returns whether an active lend is due within the inclusive window. */
    /** Returns whether an active lend is due within the inclusive window. */
    public static boolean isDueSoon(Loan loan, LocalDate date, int windowDays) {
        Objects.requireNonNull(loan, "loan");
        Objects.requireNonNull(date, "date");
        if (windowDays < 1 || loan.status() != LoanStatus.ACTIVE || loan.dueDate().isBefore(date)) {
            return false;
        }
        return !loan.dueDate().isAfter(date.plusDays(windowDays));
    }
}