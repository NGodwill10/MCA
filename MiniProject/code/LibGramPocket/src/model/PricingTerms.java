package model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public record PricingTerms(boolean baseEnabled, BigDecimal baseAmount, boolean rentEnabled, RentUnit rentUnit,
        BigDecimal rentAmount, boolean penaltyEnabled, BigDecimal penaltyPerDay, BigDecimal penaltyMaxPerBook) {
    public enum RentUnit {
        DAY, WEEK, MONTH
    }

    public PricingTerms {
        baseAmount = money(baseAmount);
        rentUnit = Objects.requireNonNull(rentUnit, "rentUnit");
        rentAmount = money(rentAmount);
        penaltyPerDay = money(penaltyPerDay);
        penaltyMaxPerBook = money(penaltyMaxPerBook);
    }

    public String toField() {
        // ASSUMPTION: OFF:<amount> preserves disabled rates; a zero penalty cap means
        // no cap.
        return "base=" + amountField(baseEnabled, baseAmount)
                + ";rent=" + (rentEnabled ? rentUnit.name() : "OFF:" + rentUnit.name()) + ":"
                + rentAmount.toPlainString()
                + ";penalty=" + (penaltyEnabled ? "" : "OFF:") + penaltyPerDay.toPlainString()
                + ":" + penaltyMaxPerBook.toPlainString();
    }

    public static PricingTerms parse(String value) {
        Objects.requireNonNull(value, "value");
        Map<String, String> fields = new HashMap<>();
        for (String entry : value.split(";", -1)) {
            int separator = entry.indexOf('=');
            if (separator <= 0 || fields.put(entry.substring(0, separator), entry.substring(separator + 1)) != null) {
                throw new IllegalArgumentException("Malformed pricing terms.");
            }
        }
        if (!fields.keySet().equals(java.util.Set.of("base", "rent", "penalty"))) {
            throw new IllegalArgumentException("Malformed pricing terms.");
        }

        ParsedAmount base = parseAmount(fields.get("base"));
        String[] rentParts = fields.get("rent").split(":", -1);
        boolean rentEnabled = rentParts.length == 2;
        if (rentParts.length == 3 && rentParts[0].equals("OFF")) {
            rentEnabled = false;
            rentParts = new String[] { rentParts[1], rentParts[2] };
        }
        if (rentParts.length != 2) {
            throw new IllegalArgumentException("Malformed rent terms.");
        }
        RentUnit unit;
        try {
            unit = RentUnit.valueOf(rentParts[0]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Malformed rent unit.", exception);
        }
        BigDecimal rentAmount = parseMoney(rentParts[1]);

        String[] penaltyParts = fields.get("penalty").split(":", -1);
        boolean penaltyEnabled = penaltyParts.length == 2;
        if (penaltyParts.length == 3 && penaltyParts[0].equals("OFF")) {
            penaltyEnabled = false;
            penaltyParts = new String[] { penaltyParts[1], penaltyParts[2] };
        }
        if (penaltyParts.length != 2) {
            throw new IllegalArgumentException("Malformed penalty terms.");
        }

        return new PricingTerms(base.enabled(), base.amount(), rentEnabled, unit, rentAmount,
                penaltyEnabled, parseMoney(penaltyParts[0]), parseMoney(penaltyParts[1]));
    }

    private static String amountField(boolean enabled, BigDecimal amount) {
        return (enabled ? "" : "OFF:") + amount.toPlainString();
    }

    private static ParsedAmount parseAmount(String value) {
        boolean enabled = !value.startsWith("OFF:");
        String amount = enabled ? value : value.substring("OFF:".length());
        return new ParsedAmount(enabled, parseMoney(amount));
    }

    private static BigDecimal money(BigDecimal value) {
        Objects.requireNonNull(value, "money value");
        if (value.signum() < 0 || value.scale() > 2 || value.compareTo(new BigDecimal("100000.00")) > 0) {
            throw new IllegalArgumentException("Money must be between 0.00 and 100000.00 with at most 2 decimals.");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static BigDecimal parseMoney(String value) {
        if (!value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Malformed money amount.");
        }
        return money(new BigDecimal(value));
    }

    private record ParsedAmount(boolean enabled, BigDecimal amount) {
    }
}