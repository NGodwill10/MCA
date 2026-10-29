import data.CsvUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public final class PerformanceSmokeTest {
    private static final int BOOKS = 10_000;
    private static final int MEMBERS = 2_000;
    private static final int LOANS = 20_000;
    private static final int CHARGES = 30_000;

    private PerformanceSmokeTest() { }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("libgram-performance-");
        try {
            long started = System.nanoTime();
            write(folder.resolve("books.txt"), List.of("bookId", "isbn", "title", "authors", "publisher",
                    "publicationYear", "edition", "category", "language", "shelfLocation", "price",
                    "totalCopies", "availableCopies", "dateAdded"), BOOKS, index -> List.of("B" + index,
                            "978000000" + String.format("%04d", index), "Demo Book " + index, "Author", "Publisher",
                            "2026", "1", "Fiction", "English", "A-1", "10.00", "3", "3", LocalDate.now().toString()));
            write(folder.resolve("members.txt"), List.of("memberId", "name", "phone", "email", "address",
                    "membershipType", "joinDate", "expiryDate", "status", "createdAt", "updatedAt"), MEMBERS,
                    index -> List.of("M" + index, "Demo Member " + index, "9000000000", "m" + index + "@example.invalid",
                            "Address", "INDIVIDUAL", "2026-01-01", "2027-01-01", "ACTIVE",
                            "2026-01-01T00:00:00", "2026-01-01T00:00:00"));
            TestSupport.check(CsvUtil.read(folder.resolve("books.txt")).size() == BOOKS + 1,
                    "Performance dataset contains all books");
            TestSupport.check(CsvUtil.read(folder.resolve("members.txt")).size() == MEMBERS + 1,
                    "Performance dataset contains all members");
            write(folder.resolve("loans.txt"), List.of("loanId", "bookId", "bookTitleSnapshot", "memberId",
                    "issueDate", "dueDate", "returnDate", "status", "baseCharge", "rentCharge", "overdueFine",
                    "returnCondition", "issuedBy", "receivedBy", "pricingSnapshot"), LOANS, index -> List.of(
                            "L" + index, "B" + (index % BOOKS), "Demo Book", "M" + (index % MEMBERS),
                            "2026-01-01", "2026-01-15", "", "ACTIVE", "0.00", "0.00", "0.00", "", "demo", "",
                            "base=0;rent=DAY:0;penalty=0:0"));
            write(folder.resolve("charges.txt"), List.of("chargeId", "memberId", "loanId", "type", "description",
                    "amount", "waivedAmount", "paidAmount", "status", "createdAt"), CHARGES, index -> List.of(
                            "C" + index, "M" + (index % MEMBERS), "L" + (index % LOANS), "OVERDUE_FINE",
                            "Demo fine", "2.00", "0.00", "0.00", "UNPAID", "2026-01-01T00:00:00"));
            TestSupport.check(CsvUtil.read(folder.resolve("loans.txt")).size() == LOANS + 1,
                    "Performance dataset contains all loans");
            TestSupport.check(CsvUtil.read(folder.resolve("charges.txt")).size() == CHARGES + 1,
                    "Performance dataset contains all charges");
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            TestSupport.check(elapsedMillis < 3_000, "Books and members load within the Phase 9 target: " + elapsedMillis + " ms");
            System.out.println("Performance smoke test passed in " + elapsedMillis + " ms.");
        } finally {
            TestSupport.deleteTree(folder);
        }
    }

    private static void write(Path file, List<String> header, int count, RowFactory factory) throws Exception {
        List<List<String>> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) rows.add(factory.row(index));
        CsvUtil.writeAtomically(file, header, rows);
    }

    @FunctionalInterface
    private interface RowFactory { List<String> row(int index); }
}
