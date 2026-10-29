package services;

import model.Loan;

public interface LostBookEventListener {
    void onBookLost(Loan loan);
    LostBookEventListener NO_OP = loan -> { };
}
