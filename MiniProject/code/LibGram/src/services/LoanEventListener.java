package services;

import model.Loan;

@FunctionalInterface
public interface LoanEventListener {
    void onLoanCreated(Loan loan);
    default void onLendingBlocked(String memberId, String reason) { }

    LoanEventListener NO_OP = loan -> { };
}
