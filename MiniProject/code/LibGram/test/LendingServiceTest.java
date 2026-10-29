import data.CsvBookRepository;
import data.CsvLoanRepository;
import data.CsvMemberRepository;
import data.CsvSettingsRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import model.Book;
import model.LoanStatus;
import model.Member;
import model.MemberStatus;
import model.MembershipType;
import model.User;
import model.User.Role;
import services.CatalogService;
import services.LendingService;
import services.MemberService;
import services.SettingsService;

public final class LendingServiceTest {
    private LendingServiceTest() { }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("libgram-lending-phase5-");
        try {
            CsvBookRepository books = new CsvBookRepository(directory.resolve("books.txt"));
            CsvMemberRepository members = new CsvMemberRepository(directory.resolve("members.txt"));
            CsvLoanRepository loans = new CsvLoanRepository(directory.resolve("loans.txt"));
            SettingsService settings = new SettingsService(new CsvSettingsRepository(directory.resolve("settings.txt")));
            CatalogService catalog = new CatalogService(books, loans);
            User admin = user(Role.ADMIN, "admin");
            User staff = user(Role.STAFF, "staff");
            Book first = catalog.addBook(admin, book("1111111111", "First", 2));
            Book second = catalog.addBook(admin, book("2222222222", "Second", 1));
            Member member = new MemberService(members, settings).add(admin, new Member("", "Reader", "1234567", "reader@example.com", "", MembershipType.INDIVIDUAL, LocalDate.now(), LocalDate.now().plusDays(365), null, null, null));
            LendingService lending = new LendingService(books, members, loans, settings);
            List<LendingService.LendingRequest> requests = List.of(
                    new LendingService.LendingRequest(first.bookId(), lending.defaultDueDate()),
                    new LendingService.LendingRequest(second.bookId(), lending.defaultDueDate()));
            lending.lend(staff, member.memberId(), requests);
            assertEquals(2, lending.activeLoansForMember(member.memberId()).size(), "Two active loans recorded");
            assertEquals(1, books.findAll().stream().filter(book -> book.bookId().equals(first.bookId())).findFirst().orElseThrow().availableCopies(), "Inventory decremented");
            assertEquals(true, loans.findAll().getFirst().pricingSnapshot().contains("base="), "Pricing snapshot exists");
            try {
                lending.lend(staff, member.memberId(), List.of(new LendingService.LendingRequest(first.bookId(), lending.defaultDueDate())));
                throw new AssertionError("Duplicate active loan must fail");
            } catch (IllegalArgumentException expected) { }
            System.out.println("Lending service tests passed.");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static Book book(String isbn, String title, int copies) {
        return new Book("", isbn, title, "Author", "Publisher", "2025", "1", "Fiction", "English", "A-1",
                BigDecimal.TEN, copies, copies, LocalDate.now());
    }

    private static User user(Role role, String username) {
        return new User(username, username, username, role, "salt", "hash", true, LocalDateTime.now());
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": expected " + expected + ", got " + actual);
    }
}
