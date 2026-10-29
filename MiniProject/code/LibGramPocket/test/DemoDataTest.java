import data.AccountPaths;
import data.CsvBookRepository;
import data.CsvLoanRepository;
import data.CsvUserRepository;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DemoDataTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("libgrampocket-demo-test-");
        try {
            Path storage = root.resolve("storage-demo");
            DemoData.generate(storage, false);
            var users = new CsvUserRepository(storage.resolve("users.txt")).findAll();
            check(users.size() == 1 && users.getFirst().username().equals("demo"));
            Path account = AccountPaths.forUsername(storage.resolve("users"), "demo");
            check(new CsvBookRepository(account.resolve("books.txt")).findAll().size() == 80);
            check(new CsvLoanRepository(account.resolve("loans.txt")).findAll().size() == 120);
            try {
                DemoData.generate(storage, false);
                throw new AssertionError("Existing demo data must not be overwritten");
            } catch (java.io.IOException expected) {
                // Existing demo storage remains unchanged.
            }
            System.out.println("DemoDataTest passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Demo data assertion failed");
        }
    }
}