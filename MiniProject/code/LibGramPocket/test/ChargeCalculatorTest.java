import java.math.BigDecimal;
import java.time.LocalDate;
import model.Bill;
import model.PricingTerms;
import model.PricingTerms.RentUnit;
import model.ReturnCondition;
import services.ChargeCalculator;
import services.LoanRules;

public final class ChargeCalculatorTest {
        private static final LocalDate ISSUE = LocalDate.of(2026, 1, 1);
        private static final PricingTerms STANDARD = new PricingTerms(true, new BigDecimal("5.00"), true,
                        RentUnit.WEEK, new BigDecimal("10.00"), true, new BigDecimal("1.00"), new BigDecimal("50.00"));

        public static void main(String[] args) {
                checkTotal(STANDARD, ISSUE.plusDays(8), ISSUE.plusDays(20), ReturnCondition.GOOD, BigDecimal.ZERO,
                                "25.00");
                checkTotal(STANDARD, ISSUE.plusDays(8), ISSUE.plusDays(5), ReturnCondition.GOOD, BigDecimal.ZERO,
                                "28.00");
                checkTotal(STANDARD, ISSUE, ISSUE, ReturnCondition.GOOD, BigDecimal.ZERO, "15.00");
                checkTotal(STANDARD, ISSUE.plusDays(80), ISSUE, ReturnCondition.GOOD, BigDecimal.ZERO, "175.00");
                checkTotal(STANDARD, ISSUE.plusDays(8), ISSUE.plusDays(8), ReturnCondition.MINOR_DAMAGE,
                                new BigDecimal("15.00"), "40.00");
                Bill roundedDamage = ChargeCalculator.calculateReturn(offTerms(), ISSUE, ISSUE, ISSUE,
                                ReturnCondition.MINOR_DAMAGE, new BigDecimal("1.235"));
                check(roundedDamage.total().equals(new BigDecimal("1.24")));

                PricingTerms monthly = new PricingTerms(false, BigDecimal.ZERO, true, RentUnit.MONTH,
                                new BigDecimal("40.00"), false, BigDecimal.ZERO, BigDecimal.ZERO);
                checkTotal(monthly, ISSUE.plusDays(31), ISSUE.plusDays(31), ReturnCondition.GOOD, BigDecimal.ZERO,
                                "80.00");
                checkTotal(monthly, ISSUE.plusDays(30), ISSUE.plusDays(30), ReturnCondition.GOOD, BigDecimal.ZERO,
                                "40.00");
                PricingTerms weekly = new PricingTerms(false, BigDecimal.ZERO, true, RentUnit.WEEK,
                                new BigDecimal("10.00"), false, BigDecimal.ZERO, BigDecimal.ZERO);
                checkTotal(weekly, ISSUE.plusDays(7), ISSUE.plusDays(7), ReturnCondition.GOOD, BigDecimal.ZERO,
                                "10.00");
                checkTotal(weekly, ISSUE.plusDays(8), ISSUE.plusDays(8), ReturnCondition.GOOD, BigDecimal.ZERO,
                                "20.00");
                PricingTerms off = new PricingTerms(false, BigDecimal.ZERO, false, RentUnit.DAY, BigDecimal.ZERO,
                                false, BigDecimal.ZERO, BigDecimal.ZERO);
                checkTotal(off, ISSUE, ISSUE, ReturnCondition.GOOD, BigDecimal.ZERO, "0.00");

                Bill cap = ChargeCalculator.calculateReturn(STANDARD, ISSUE, ISSUE, ISSUE.plusDays(50),
                                ReturnCondition.GOOD, BigDecimal.ZERO);
                check(cap.lines().stream().filter(line -> line.label().equals("Overdue penalty")).findFirst()
                                .orElseThrow().amount().equals(new BigDecimal("50.00")));
                Bill uncapped = ChargeCalculator.calculateReturn(new PricingTerms(false, BigDecimal.ZERO, false,
                                RentUnit.DAY, BigDecimal.ZERO, true, new BigDecimal("1.00"), BigDecimal.ZERO),
                                ISSUE, ISSUE, ISSUE.plusDays(80), ReturnCondition.GOOD, BigDecimal.ZERO);
                check(uncapped.total().equals(new BigDecimal("80.00")));
                Bill rounded005 = ChargeCalculator.calculateReturn(offTerms(), ISSUE, ISSUE, ISSUE,
                                ReturnCondition.MINOR_DAMAGE, new BigDecimal("0.005"));
                check(rounded005.total().equals(new BigDecimal("0.01")));
                Bill rounded2675 = ChargeCalculator.calculateReturn(offTerms(), ISSUE, ISSUE, ISSUE,
                                ReturnCondition.MINOR_DAMAGE, new BigDecimal("2.675"));
                check(rounded2675.total().equals(new BigDecimal("2.68")));
                BigDecimal maximumRate = new BigDecimal("100000.00");
                check(ChargeCalculator
                                .baseCharge(new PricingTerms(true, maximumRate, false, RentUnit.DAY, BigDecimal.ZERO,
                                                false, BigDecimal.ZERO, BigDecimal.ZERO))
                                .equals(maximumRate));
                BigDecimal large = new BigDecimal("999999999999999999.99");
                Bill largeDamage = ChargeCalculator.calculateReturn(offTerms(), ISSUE, ISSUE, ISSUE,
                                ReturnCondition.MINOR_DAMAGE, large);
                check(largeDamage.total().equals(large));
                Bill severe = ChargeCalculator.calculateReturn(offTerms(), ISSUE, ISSUE, ISSUE,
                                ReturnCondition.SEVERE_DAMAGE, new BigDecimal("300.00"));
                check(severe.lines().stream().anyMatch(line -> line.label().equals("Damage")
                                && line.amount().equals(new BigDecimal("300.00"))));
                check(LoanRules.daysOverdue(ISSUE, ISSUE) == 0);
                check(LoanRules.daysOverdue(ISSUE, ISSUE.plusDays(3)) == 3);
                System.out.println("ChargeCalculatorTest passed");
        }

        private static void checkTotal(PricingTerms terms, LocalDate returned, LocalDate due,
                        ReturnCondition condition, BigDecimal damage, String expected) {
                Bill bill = ChargeCalculator.calculateReturn(terms, ISSUE, due, returned, condition, damage);
                check(bill.total().equals(new BigDecimal(expected)));
        }

        private static void check(boolean condition) {
                if (!condition) {
                        throw new AssertionError("Charge calculation assertion failed");
                }
        }

        private static PricingTerms offTerms() {
                return new PricingTerms(false, BigDecimal.ZERO, false, RentUnit.DAY, BigDecimal.ZERO,
                                false, BigDecimal.ZERO, BigDecimal.ZERO);
        }
}