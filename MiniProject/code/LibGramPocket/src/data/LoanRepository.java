package data;

import java.io.IOException;
import java.util.List;
import model.Loan;

public interface LoanRepository {
    List<Loan> findAll() throws IOException;

    void saveAll(List<Loan> loans) throws IOException;

    static LoanRepository empty() {
        return new LoanRepository() {
            @Override
            public List<Loan> findAll() {
                return List.of();
            }

            @Override
            public void saveAll(List<Loan> loans) {
                throw new UnsupportedOperationException("This repository is read-only and empty.");
            }
        };
    }
}