package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public record Bill(List<Line> lines, BigDecimal total) {
    public Bill(List<Line> lines) {
        this(List.copyOf(lines), lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP));
    }

    public Bill {
        lines = List.copyOf(lines);
        BigDecimal calculatedTotal = lines.stream().map(Line::amount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        if (!Objects.requireNonNull(total, "total").setScale(2, RoundingMode.HALF_UP).equals(calculatedTotal)) {
            throw new IllegalArgumentException("Bill total must match its line items.");
        }
        total = calculatedTotal;
    }

    public record Line(String label, BigDecimal amount) {
        public Line {
            Objects.requireNonNull(label, "label");
            amount = Objects.requireNonNull(amount, "amount").setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Bill amounts cannot be negative.");
            }
        }
    }
}