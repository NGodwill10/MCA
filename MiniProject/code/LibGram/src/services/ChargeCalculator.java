package services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import model.Book;
import model.ChargeType;
import model.Loan;
import model.ReturnCondition;

/**
 * Pure billing calculations. Rates for rent and fines are read only from a loan
 * snapshot.
 */
public final class ChargeCalculator {
    private ChargeCalculator() {
    }

    public record Line(ChargeType type, String description, BigDecimal amount) {
        public Line {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(description, "description");
            amount = money(amount);
            if (amount.signum() < 0)
                throw new IllegalArgumentException("Charge lines cannot be negative.");
        }
    }

    public static List<Line> returnCharges(Loan loan, Book book, LocalDate returnedOn, ReturnCondition condition,
            BigDecimal minorDamage, BigDecimal damagedProcessing) {
        Objects.requireNonNull(loan, "loan");
        Objects.requireNonNull(book, "book");
        Objects.requireNonNull(returnedOn, "returnedOn");
        Objects.requireNonNull(condition, "condition");
        if (returnedOn.isBefore(loan.issueDate()))
            throw new IllegalArgumentException("Return date precedes issue date.");
        if (condition == ReturnCondition.MINOR_DAMAGE && money(minorDamage).signum() < 0) {
            throw new IllegalArgumentException("Damage charges cannot be negative.");
        }
        if (money(damagedProcessing).signum() < 0) {
            throw new IllegalArgumentException("Processing fees cannot be negative.");
        }
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(ChargeType.BASE, "Base charge", loan.baseCharge()));
        Snapshot snapshot = Snapshot.parse(loan.pricingSnapshot());
        long daysHeld = Math.max(0, ChronoUnit.DAYS.between(loan.issueDate(), returnedOn));
        if (snapshot.rent.signum() > 0)
            lines.add(new Line(ChargeType.RENT, "Rent",
                    snapshot.rent.multiply(BigDecimal.valueOf(units(daysHeld, snapshot.unitLength)))));
        long overdue = Math.max(0, ChronoUnit.DAYS.between(loan.dueDate(), returnedOn));
        BigDecimal fine = snapshot.fine.multiply(BigDecimal.valueOf(overdue));
        if (snapshot.fineCap.signum() > 0)
            fine = fine.min(snapshot.fineCap);
        lines.add(new Line(ChargeType.OVERDUE_FINE, "Overdue fine", fine));
        if (condition == ReturnCondition.MINOR_DAMAGE)
            lines.add(new Line(ChargeType.DAMAGED_BOOK, "Minor damage", minorDamage));
        if (condition == ReturnCondition.SEVERE_DAMAGE) {
            lines.add(new Line(ChargeType.DAMAGED_BOOK, "Severe damage", book.price()));
            lines.add(new Line(ChargeType.PROCESSING_FEE, "Damaged-book processing", damagedProcessing));
        }
        return lines.stream().filter(line -> line.amount().signum() != 0).toList();
    }

    public static List<Line> lostCharges(Loan loan, Book book, LocalDate lostOn, BigDecimal processingFee) {
        if (money(processingFee).signum() < 0)
            throw new IllegalArgumentException("Processing fees cannot be negative.");
        List<Line> result = new ArrayList<>(
                returnCharges(loan, book, lostOn, ReturnCondition.GOOD, BigDecimal.ZERO, BigDecimal.ZERO));
        result.removeIf(line -> line.type() == ChargeType.RENT || line.type() == ChargeType.BASE);
        result.add(new Line(ChargeType.LOST_BOOK, "Lost book: " + book.title(), book.price()));
        result.add(new Line(ChargeType.PROCESSING_FEE, "Lost-book processing", processingFee));
        return result.stream().filter(line -> line.amount().signum() != 0).toList();
    }

    public static long daysHeld(Loan loan, LocalDate returnedOn) {
        return Math.max(0, ChronoUnit.DAYS.between(loan.issueDate(), returnedOn));
    }

    public static long daysOverdue(Loan loan, LocalDate date) {
        return Math.max(0, ChronoUnit.DAYS.between(loan.dueDate(), date));
    }

    public static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static long units(long days, int length) {
        return Math.max(1, (days + length - 1) / length);
    }

    private record Snapshot(BigDecimal rent, int unitLength, BigDecimal fine, BigDecimal fineCap) {
        static Snapshot parse(String text) {
            BigDecimal rent = BigDecimal.ZERO, fine = BigDecimal.ZERO, cap = BigDecimal.ZERO;
            int length = 1;
            for (String token : text == null ? new String[0] : text.split(";")) {
                String[] pair = token.split("=", 2);
                if (pair.length != 2)
                    continue;
                if (pair[0].equals("rent")) {
                    String[] p = pair[1].split(":");
                    if (p.length == 2) {
                        rent = decimal(p[1]);
                        length = switch (p[0]) {
                            case "WEEK" -> 7;
                            case "MONTH" -> 30;
                            default -> 1;
                        };
                    }
                }
                if (pair[0].equals("penalty")) {
                    String[] p = pair[1].split(":");
                    if (p.length == 2) {
                        fine = decimal(p[0]);
                        cap = decimal(p[1]);
                    }
                }
            }
            return new Snapshot(rent, length, fine, cap);
        }

        private static BigDecimal decimal(String text) {
            try {
                return money(new BigDecimal(text));
            } catch (NumberFormatException ex) {
                return BigDecimal.ZERO.setScale(2);
            }
        }
    }
}
