package data;

import java.io.IOException;
import java.util.List;
import model.Loan;

public interface LoanRepository {
    List<Loan> findAll();

    void saveAll(List<Loan> loans) throws IOException;
}
