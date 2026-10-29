package services;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import model.Loan;

public final class LoanRules {
    private LoanRules() { }

    public static long daysOverdue(Loan loan, LocalDate today) {
        return Math.max(0, ChronoUnit.DAYS.between(loan.dueDate(), today));
    }

    public static long daysOverdue(Loan loan) {
        return daysOverdue(loan, LocalDate.now());
    }

    public static boolean dueSoon(Loan loan, LocalDate today) {
        return !loan.dueDate().isBefore(today) && !loan.dueDate().isAfter(today.plusDays(3));
    }

    public static boolean dueSoon(Loan loan) {
        return dueSoon(loan, LocalDate.now());
    }
}
