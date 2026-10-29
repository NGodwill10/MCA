package ui;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DisplayFormat {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm",
            Locale.ENGLISH);

    private DisplayFormat() {
    }

    public static String date(LocalDate value) {
        return DATE.format(value);
    }

    public static String dateTime(LocalDateTime value) {
        return DATE_TIME.format(value);
    }

    public static String money(BigDecimal value) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return "Rs. " + format.format(value.setScale(2, RoundingMode.HALF_UP));
    }
}