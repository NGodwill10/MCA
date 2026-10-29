package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Objects;
import model.Bill;
import model.PricingTerms;
import model.PricingTerms.RentUnit;
import model.ReturnCondition;

public final class ChargeCalculator {
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private ChargeCalculator() {
    }

    /** Returns the enabled base charge for one book. */
    /** Returns the enabled base charge for one book. */
    public static BigDecimal baseCharge(PricingTerms terms) {
        return terms.baseEnabled() ? money(terms.baseAmount()) : ZERO;
    }

    /** Calculates an itemized return bill from the rates snapshotted on the loan. */
    /** Calculates an itemized return bill from the rates snapshotted on the loan. */
    public static Bill calculateReturn(PricingTerms terms, LocalDate issueDate, LocalDate dueDate,
            LocalDate returnDate, ReturnCondition condition, BigDecimal damageAmount) {
        Objects.requireNonNull(terms, "terms");
        Objects.requireNonNull(issueDate, "issueDate");
        Objects.requireNonNull(dueDate, "dueDate");
        Objects.requireNonNull(returnDate, "returnDate");
        Objects.requireNonNull(condition, "condition");
        if (returnDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("Return date cannot be before the issue date.");
        }

        ArrayList<Bill.Line> lines = new ArrayList<>();
        addLine(lines, "Base charge", baseCharge(terms));
        long daysHeld = Math.max(0, ChronoUnit.DAYS.between(issueDate, returnDate));
        if (terms.rentEnabled()) {
            long unitLength = switch (terms.rentUnit()) {
                case DAY -> 1;
                case WEEK -> 7;
                case MONTH -> 30;
            };
            long units = Math.max(1, (daysHeld + unitLength - 1) / unitLength);
            addLine(lines, "Rent", terms.rentAmount().multiply(BigDecimal.valueOf(units)));
        }
        if (terms.penaltyEnabled()) {
            long daysOverdue = LoanRules.daysOverdue(dueDate, returnDate);
            BigDecimal penalty = terms.penaltyPerDay().multiply(BigDecimal.valueOf(daysOverdue));
            if (terms.penaltyMaxPerBook().signum() > 0) {
                penalty = penalty.min(terms.penaltyMaxPerBook());
            }
            addLine(lines, "Overdue penalty", penalty);
        }
        if (condition != ReturnCondition.GOOD) {
            BigDecimal damage = Objects.requireNonNull(damageAmount, "damageAmount");
            if (damage.signum() < 0) {
                throw new IllegalArgumentException("Damage amount cannot be negative.");
            }
            addLine(lines, "Damage", damage);
        }
        return new Bill(lines);
    }

    /** Calculates accrued base, rent, and overdue charges through the supplied date. */
    /** Calculates accrued base, rent, and overdue charges through the supplied date. */
    public static Bill calculateAccrued(PricingTerms terms, LocalDate issueDate, LocalDate dueDate,
            LocalDate asOfDate) {
        return calculateReturn(terms, issueDate, dueDate, asOfDate, ReturnCondition.GOOD, ZERO);
    }

    private static void addLine(ArrayList<Bill.Line> lines, String label, BigDecimal amount) {
        BigDecimal rounded = money(amount);
        if (rounded.signum() > 0) {
            lines.add(new Bill.Line(label, rounded));
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}