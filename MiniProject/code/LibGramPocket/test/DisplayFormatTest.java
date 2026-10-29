import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import ui.DisplayFormat;

public final class DisplayFormatTest {
    public static void main(String[] args) {
        check(DisplayFormat.money(new BigDecimal("1250")).equals("Rs. 1,250.00"));
        check(DisplayFormat.money(new BigDecimal("2.675")).equals("Rs. 2.68"));
        check(DisplayFormat.date(LocalDate.of(2026, 11, 4)).equals("04 Nov 2026"));
        check(DisplayFormat.dateTime(LocalDateTime.of(2026, 11, 4, 9, 5))
                .equals("04 Nov 2026, 09:05"));
        System.out.println("DisplayFormatTest passed");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Display formatting assertion failed");
        }
    }
}