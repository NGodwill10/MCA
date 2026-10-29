import data.CsvChargeRepository;
import data.CsvPaymentRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import model.Book;
import model.Charge;
import model.ChargeType;
import model.Loan;
import model.LoanStatus;
import model.PaymentMethod;
import model.ReturnCondition;
import services.ChargeCalculator;
import services.ChargeService;
import services.PaymentService;

public final class PhaseSixBillingTest {
    public static void main(String[] args) throws Exception {
        Loan loan = new Loan("L", "B", "Book", "M", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 11), null,
                LoanStatus.ACTIVE, money("10"), money("0"), money("0"), "", "staff", "",
                "base=0.00;rent=WEEK:20.00;penalty=2.00:100.00");
        Book book = new Book("B", "123456789X", "Book", "Author", "Pub", "2026", "1", "Cat", "EN", "A", money("450"), 2,
                1, LocalDate.now());
        var lines = ChargeCalculator.returnCharges(loan, book, LocalDate.of(2026, 1, 11), ReturnCondition.GOOD,
                money("0"), money("25"));
        check(total(lines, ChargeType.RENT).equals(money("40")), "weekly rent uses ceiling units");
        check(total(lines, ChargeType.OVERDUE_FINE).equals(money("0")), "due date has no fine");
        var capped = ChargeCalculator.returnCharges(loan, book, LocalDate.of(2026, 3, 12), ReturnCondition.GOOD,
                money("0"), money("25"));
        check(total(capped, ChargeType.OVERDUE_FINE).equals(money("100")), "fine is capped");
        checkTotal("base=0;rent=DAY:3;penalty=0:0", money("10"), LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 1, 1), ReturnCondition.GOOD, money("0"), money("0"), book, "40.00");
        checkTotal("base=0;rent=WEEK:20;penalty=0:0", money("0"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1), ReturnCondition.GOOD, money("0"), money("0"), book, "20.00");
        checkTotal("base=0;rent=MONTH:50;penalty=0:0", money("0"), LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 1, 1), ReturnCondition.GOOD, money("0"), money("0"), book, "100.00");
        checkTotal("base=0;rent=DAY:0;penalty=2:100", money("0"), LocalDate.of(2026, 1, 11),
                LocalDate.of(2026, 1, 1), ReturnCondition.GOOD, money("0"), money("0"), book, "20.00");
        checkTotal("base=0;rent=DAY:0;penalty=0:0", money("0"), LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 1), ReturnCondition.MINOR_DAMAGE, money("1.235"), money("0"), book,
                "1.24");
        var severe = ChargeCalculator.returnCharges(loan, book, LocalDate.of(2026, 1, 11),
                ReturnCondition.SEVERE_DAMAGE, money("0"), money("25"));
        check(total(severe, ChargeType.DAMAGED_BOOK).equals(money("450"))
                && total(severe, ChargeType.PROCESSING_FEE).equals(money("25")), "severe damage breakdown");
        var lostLines = ChargeCalculator.lostCharges(loan, book, LocalDate.of(2026, 1, 11), money("50"));
        check(total(lostLines, ChargeType.LOST_BOOK).equals(money("450"))
                && total(lostLines, ChargeType.PROCESSING_FEE).equals(money("50")), "lost book breakdown");
        Loan rentDisabled = new Loan("disabled-rent", "B", "Book", "M", LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 11), null, LoanStatus.ACTIVE, money("0"), money("0"), money("0"), "",
                "staff", "", "base=0;rent=DAY:0;penalty=0:0");
        check(ChargeCalculator.returnCharges(rentDisabled, book, LocalDate.of(2026, 1, 1), ReturnCondition.GOOD,
                money("0"), money("0")).stream().noneMatch(line -> line.type() == ChargeType.RENT),
                "disabled rent yields no rent charge");
        Path dir = Files.createTempDirectory("libgram-phase6-");
        ChargeService charges = new ChargeService(new CsvChargeRepository(dir.resolve("charges.txt")));
        PaymentService payments = new PaymentService(charges, new CsvPaymentRepository(dir.resolve("payments.txt")));
        charges.add("M", "", ChargeType.MEMBERSHIP_REGISTRATION, "Registration", money("100"));
        charges.add("M", "", ChargeType.CARD_REPLACEMENT, "Card", money("50"));
        check(charges.unpaidBalance("M").equals(money("150")), "ledger balance");
        var user = new model.User("STAFF", "staff", "Staff", model.User.Role.STAFF, "salt", "hash", true,
                LocalDateTime.now());
        List<Charge> original = charges.forMember("M");
        var firstReceipt = payments.pay(user, "M", original.stream().map(c -> c.chargeId()).toList(), money("100"),
                PaymentMethod.CASH);
        check(charges.unpaidBalance("M").equals(money("50")), "partial oldest-first payment");
        check(firstReceipt.payments().size() == 1
                && firstReceipt.payments().getFirst().chargeId().equals(original.getFirst().chargeId()),
                "payment allocates to oldest charge first");
        check(charges.forMember("M").getFirst().status() == model.ChargeStatus.PAID,
                "fully paid charge status is Paid");
        payments.pay(user, "M", charges.forMember("M").stream().map(c -> c.chargeId()).toList(), money("50"),
                PaymentMethod.UPI);
        check(charges.unpaidBalance("M").signum() == 0, "full payment unblocks dues");
        check(charges.forMember("M").getLast().status() == model.ChargeStatus.PAID,
                "second charge status is Paid");
        System.out.println("Phase six billing tests passed.");
    }

    private static BigDecimal total(List<ChargeCalculator.Line> lines, ChargeType type) {
        return lines.stream().filter(l -> l.type() == type).map(ChargeCalculator.Line::amount).reduce(money("0"),
                BigDecimal::add);
    }

    private static void checkTotal(String snapshot, BigDecimal base, LocalDate returned, LocalDate due,
            ReturnCondition condition, BigDecimal minorDamage, BigDecimal damagedFee, Book book, String expected) {
        Loan sample = new Loan("sample", book.bookId(), book.title(), "M", LocalDate.of(2026, 1, 1), due, null,
                LoanStatus.ACTIVE, base, money("0"), money("0"), "", "staff", "", snapshot);
        List<ChargeCalculator.Line> lines = ChargeCalculator.returnCharges(sample, book, returned, condition,
                minorDamage, damagedFee);
        BigDecimal sum = lines.stream().map(ChargeCalculator.Line::amount).reduce(money("0"), BigDecimal::add);
        check(sum.equals(money(expected)), "expected " + expected + " but got " + sum);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
    }
}
