import java.math.BigDecimal;
import model.PricingTerms;
import model.PricingTerms.RentUnit;

public final class PricingTermsTest {
    public static void main(String[] args) {
        PricingTerms terms = new PricingTerms(true, new BigDecimal("5.00"), true, RentUnit.WEEK,
                new BigDecimal("10.00"), true, new BigDecimal("1.00"), new BigDecimal("50.00"));
        check(terms.toField().equals("base=5.00;rent=WEEK:10.00;penalty=1.00:50.00"));
        check(PricingTerms.parse(terms.toField()).equals(terms));

        PricingTerms disabledAndUncapped = new PricingTerms(false, BigDecimal.ZERO, false, RentUnit.DAY,
                BigDecimal.ZERO, false, BigDecimal.ZERO, BigDecimal.ZERO);
        check(PricingTerms.parse(disabledAndUncapped.toField()).equals(disabledAndUncapped));
        PricingTerms disabledWithRememberedRate = new PricingTerms(false, new BigDecimal("3.50"), false,
                RentUnit.MONTH, new BigDecimal("4.00"), false, new BigDecimal("0.25"), BigDecimal.ZERO);
        check(PricingTerms.parse(disabledWithRememberedRate.toField()).equals(disabledWithRememberedRate));
        PricingTerms awkwardAmounts = new PricingTerms(true, new BigDecimal("0.01"), true, RentUnit.MONTH,
                new BigDecimal("99999.99"), false, new BigDecimal("0.01"), new BigDecimal("100000.00"));
        check(PricingTerms.parse(awkwardAmounts.toField()).equals(awkwardAmounts));
        expectMalformed("base=5;rent=FORTNIGHT:2;penalty=1:0");
        expectMalformed("base=5;rent=DAY:2;penalty=1:0;other=x");
        System.out.println("PricingTermsTest passed");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Pricing terms assertion failed");
        }
    }

    private static void expectMalformed(String value) {
        try {
            PricingTerms.parse(value);
            throw new AssertionError("Expected malformed pricing terms to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected malformed input.
        }
    }
}