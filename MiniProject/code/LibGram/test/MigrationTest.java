import data.CsvChargeRepository;
import data.CsvLoanRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import model.LoanStatus;
import services.ChargeService;

public final class MigrationTest {
    private MigrationTest() { }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("libgram-migration-");
        try {
            CsvLoanRepository loans = new CsvLoanRepository(folder.resolve("loans.txt"));
            loans.saveAll(List.of(new model.Loan("active", "B", "Book B", "M", LocalDate.now(),
                    LocalDate.now().plusDays(7), null, LoanStatus.ACTIVE, TestSupport.money("10"), TestSupport.money("0"),
                    TestSupport.money("0"), "", "staff", "", "base=10;rent=DAY:0;penalty=0:0")));
            ChargeService charges = new ChargeService(new CsvChargeRepository(folder.resolve("charges.txt")));
            TestSupport.equals(1, charges.migrateActiveLoanBases(loans), "First base-charge migration creates one charge");
            TestSupport.equals(0, charges.migrateActiveLoanBases(loans), "Second base-charge migration is idempotent");
            System.out.println("Migration tests passed.");
        } finally {
            TestSupport.deleteTree(folder);
        }
    }
}
