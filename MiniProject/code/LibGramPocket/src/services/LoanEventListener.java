package services;

import model.Loan;

@FunctionalInterface
public interface LoanEventListener {
    LoanEventListener NO_OP = (loan, change) -> {
    };

    void onLoanChanged(Loan loan, Change change);

    enum Change {
        LENT,
        BORROWED,
        RETURNED,
        PAYMENT_UPDATED
    }
}