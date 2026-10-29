import data.CsvLoanRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import model.Loan;
import model.LoanDirection;
import model.LoanStatus;
import model.PaymentStatus;

public final class CsvLoanRepositoryTest {
    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("pocket-loans-test");
        Path file = folder.resolve("loans.txt");
        CsvLoanRepository repository = new CsvLoanRepository(file);
        Loan active = new Loan("loan-1", LoanDirection.LENT, "book-1", "A, quoted \"title\"", "contact-1",
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 15), null, LoanStatus.ACTIVE,
                new BigDecimal("5.00"), null, null, null, null, PaymentStatus.NONE,
                "base=5.00;rent=WEEK:10.00;penalty=1.00:50.00");
        repository.saveAll(java.util.List.of(active));
        check(new CsvLoanRepository(file).findAll().equals(java.util.List.of(active)));
        check(Files.readString(file).contains(",Active,"));
        check(Files.exists(folder.resolve("loans.txt.bak")));
        check(new CsvLoanRepository(folder.resolve("other-account/loans.txt")).findAll().isEmpty());
        System.out.println("CsvLoanRepositoryTest passed");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Loan CSV assertion failed");
        }
    }
}