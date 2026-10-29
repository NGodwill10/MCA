import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import model.Book;
import model.Loan;
import model.LoanStatus;
import model.Member;
import model.MemberStatus;
import model.MembershipType;
import model.User;

final class TestSupport {
    private TestSupport() { }

    static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    static User user(User.Role role, boolean active) {
        return new User(role.name().toLowerCase(), role.name().toLowerCase(), role.name(), role,
                "salt", "hash", active, LocalDateTime.now());
    }

    static Member member(String id, String name) {
        return new Member(id, name, "1234567890", id + "@example.invalid", "",
                MembershipType.INDIVIDUAL, LocalDate.now(), LocalDate.now().plusDays(365), MemberStatus.ACTIVE,
                LocalDateTime.now(), LocalDateTime.now());
    }

    static Book book(String id, int total, int available) {
        return new Book(id, "9780000000000", "Book " + id, "Author", "Publisher", "2025", "1",
                "Fiction", "English", "A-1", money("100"), total, available, LocalDate.now());
    }

    static Loan loan(String id, String bookId, String memberId, LocalDate issue, LocalDate due, LoanStatus status) {
        return new Loan(id, bookId, "Book " + bookId, memberId, issue, due, null, status, money("0"), money("0"),
                money("0"), "", "staff", "", "base=0;rent=DAY:0;penalty=0:0");
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    static void equals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }

    static void deleteTree(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
}
