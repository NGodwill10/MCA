import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import model.Book;
import model.ChargeType;
import model.Loan;
import model.LoanStatus;
import model.ReturnCondition;
import services.ChargeCalculator;

public final class ChargeCalculatorTest {
    private ChargeCalculatorTest() { }

    public static void main(String[] args) {
        Book book = TestSupport.book("B", 1, 1);
        LocalDate issue = LocalDate.of(2026, 1, 1);
        Loan weekly = loan(issue, issue.plusDays(10), "base=10;rent=WEEK:20;penalty=2:100");
        checkTypeTotal(weekly, book, issue.plusDays(10), ChargeType.RENT, "40.00");
        checkTypeTotal(weekly, book, issue, ChargeType.RENT, "20.00");
        checkTypeTotal(weekly, book, issue.plusDays(60), ChargeType.OVERDUE_FINE, "100.00");
        checkTypeTotal(loan(issue, issue, "base=0;rent=DAY:3;penalty=0:0"), book, issue.plusDays(7),
                ChargeType.RENT, "21.00");
        checkTypeTotal(loan(issue, issue, "base=0;rent=MONTH:50;penalty=0:0"), book, issue.plusDays(30),
                ChargeType.RENT, "50.00");
        checkTypeTotal(loan(issue, issue, "base=0;rent=MONTH:50;penalty=0:0"), book, issue.plusDays(31),
                ChargeType.RENT, "100.00");
        Loan disabled = new Loan("disabled", "B", "Book B", "M", issue, issue, null, LoanStatus.ACTIVE,
                TestSupport.money("0"), TestSupport.money("0"), TestSupport.money("0"), "", "staff", "",
                "base=0;rent=DAY:0;penalty=0:0");
        List<ChargeCalculator.Line> allOff = ChargeCalculator.returnCharges(
                disabled, book, issue, ReturnCondition.GOOD,
                BigDecimal.ZERO, BigDecimal.ZERO);
        TestSupport.check(allOff.isEmpty(), "All disabled charges produce no lines");
        TestSupport.equals(TestSupport.money("0.01"), ChargeCalculator.money(new BigDecimal("0.005")),
                "HALF_UP rounds 0.005");
        TestSupport.equals(TestSupport.money("2.68"), ChargeCalculator.money(new BigDecimal("2.675")),
                "HALF_UP rounds 2.675");
        try {
            ChargeCalculator.returnCharges(weekly, book, issue.minusDays(1), ReturnCondition.GOOD,
                    BigDecimal.ZERO, BigDecimal.ZERO);
            throw new AssertionError("Return before issue date must fail");
        } catch (IllegalArgumentException expected) { }
        System.out.println("Charge calculator tests passed.");
    }

    private static Loan loan(LocalDate issue, LocalDate due, String snapshot) {
        return new Loan("L", "B", "Book B", "M", issue, due, null, LoanStatus.ACTIVE, TestSupport.money("10"),
                TestSupport.money("0"), TestSupport.money("0"), "", "staff", "", snapshot);
    }

    private static void checkTypeTotal(Loan loan, Book book, LocalDate returned, ChargeType type, String expected) {
        BigDecimal total = ChargeCalculator.returnCharges(loan, book, returned, ReturnCondition.GOOD,
                BigDecimal.ZERO, BigDecimal.ZERO).stream().filter(line -> line.type() == type)
                .map(ChargeCalculator.Line::amount).reduce(TestSupport.money("0"), BigDecimal::add);
        TestSupport.equals(TestSupport.money(expected), total, type + " total");
    }
}
