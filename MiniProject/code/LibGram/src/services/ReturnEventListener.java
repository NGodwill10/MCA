package services;

import java.math.BigDecimal;
import model.Loan;

@FunctionalInterface
public interface ReturnEventListener {
    void onReturned(Loan loan, boolean damaged, BigDecimal overdueFine);
    ReturnEventListener NO_OP = (loan, damaged, fine) -> { };
}
