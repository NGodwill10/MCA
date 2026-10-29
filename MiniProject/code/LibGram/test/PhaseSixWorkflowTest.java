import data.CsvBookRepository;
import data.CsvChargeRepository;
import data.CsvLoanRepository;
import data.CsvMemberRepository;
import data.CsvPaymentRepository;
import data.CsvSettingsRepository;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import model.Book;
import model.Charge;
import model.ChargeStatus;
import model.ChargeType;
import model.Loan;
import model.LoanStatus;
import model.Member;
import model.MemberStatus;
import model.MembershipType;
import model.PaymentMethod;
import model.ReturnCondition;
import model.User;
import services.ChargeService;
import services.LendingService;
import services.MemberService;
import services.PaymentService;
import services.PermissionPolicy;
import services.RealDuesProvider;
import services.ReturnService;
import services.SettingsService;

public final class PhaseSixWorkflowTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("libgram-phase6-workflow-");
        try {
            verifyChargeStates();
            verifyReturnsLostAndRefund(root.resolve("returns"));
            verifyDuesBlockAndRegistration(root.resolve("lending"));
            verifyAutomaticLostThreshold(root.resolve("automatic-lost"));
            System.out.println("Phase six workflow tests passed.");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyChargeStates() {
        Charge charge = new Charge("C", "M", "", ChargeType.CARD_REPLACEMENT, "Card", money("100"),
                money("0"), money("0"), ChargeStatus.UNPAID, LocalDateTime.now());
        Charge partial = charge.withAmounts(money("0"), money("40"));
        check(partial.status() == ChargeStatus.PARTIAL && partial.balance().equals(money("60")));
        Charge paid = partial.withAmounts(money("0"), money("100"));
        check(paid.status() == ChargeStatus.PAID && paid.balance().signum() == 0);
        check(charge.withAmounts(money("100"), money("0")).status() == ChargeStatus.WAIVED);
        Charge refund = new Charge("R", "M", "L", ChargeType.REFUND, "Refund", money("-450"), money("0"),
                money("0"), ChargeStatus.UNPAID, LocalDateTime.now());
        check(refund.withAmounts(money("0"), money("-450")).status() == ChargeStatus.PAID);
    }

    private static void verifyReturnsLostAndRefund(Path folder) throws Exception {
        Files.createDirectories(folder);
        CsvBookRepository books = new CsvBookRepository(folder.resolve("books.txt"));
        CsvLoanRepository loans = new CsvLoanRepository(folder.resolve("loans.txt"));
        CsvChargeRepository chargeRepository = new CsvChargeRepository(folder.resolve("charges.txt"));
        SettingsService settings = new SettingsService(new CsvSettingsRepository(folder.resolve("settings.txt")));
        ChargeService charges = new ChargeService(chargeRepository);
        ReturnService returns = new ReturnService(books, loans, charges, settings);
        User staff = user(User.Role.STAFF, "desk");
        LocalDate today = LocalDate.now();
        Book book = book("book-normal", 2, 1);
        Loan normal = loan("normal", book.bookId(), today.minusDays(10), today.minusDays(10),
                "base=10.00;rent=DAY:3.00;penalty=2.00:100.00");
        books.saveAll(List.of(book));
        loans.saveAll(List.of(normal));
        charges.add("member", normal.loanId(), ChargeType.BASE, "Base charge", money("10"));
        Loan returned = returns.receive(staff, normal.loanId(), ReturnCondition.GOOD, money("0"));
        check(returned.status() == LoanStatus.RETURNED);
        check(returned.rentCharge().equals(money("30")) && returned.overdueFine().equals(money("20")));
        check(books.findAll().getFirst().availableCopies() == 2);
        check(charges.forMember("member").stream().anyMatch(charge -> charge.type() == ChargeType.RENT
                && charge.amount().equals(money("30"))));

        Book severeBook = book("book-severe", 2, 1);
        Loan severe = loan("severe", severeBook.bookId(), today.minusDays(1), today.plusDays(10),
                "base=0.00;rent=DAY:0.00;penalty=0.00:0.00");
        books.saveAll(List.of(books.findAll().getFirst(), severeBook));
        loans.saveAll(List.of(returned, severe));
        returns.receive(staff, severe.loanId(), ReturnCondition.SEVERE_DAMAGE, money("0"));
        Book withdrawn = books.findAll().stream().filter(item -> item.bookId().equals(severeBook.bookId()))
                .findFirst().orElseThrow();
        check(withdrawn.totalCopies() == 1 && withdrawn.availableCopies() == 1);
        check(charges.forMember("member").stream().anyMatch(charge -> charge.loanId().equals(severe.loanId())
                && charge.type() == ChargeType.DAMAGED_BOOK && charge.amount().equals(money("450"))));

        Book lostBook = book("book-lost", 2, 1);
        Loan lostLoan = loan("lost-paid", lostBook.bookId(), today.minusDays(2), today.minusDays(1),
                "base=0.00;rent=DAY:0.00;penalty=2.00:100.00");
        books.saveAll(List.of(books.findAll().get(0), books.findAll().get(1), lostBook));
        loans.saveAll(List.of(returned, loans.findAll().get(1), lostLoan));
        services.LostBookService lost = new services.LostBookService(books, loans, charges, settings);
        lost.markLost(staff, lostLoan.loanId());
        Book afterLost = books.findAll().stream().filter(item -> item.bookId().equals(lostBook.bookId()))
                .findFirst().orElseThrow();
        check(afterLost.totalCopies() == 1 && afterLost.availableCopies() == 1);
        Charge lostCost = charges.forMember("member").stream()
                .filter(charge -> charge.loanId().equals(lostLoan.loanId()) && charge.type() == ChargeType.LOST_BOOK)
                .findFirst().orElseThrow();
        PaymentService payments = new PaymentService(charges, new CsvPaymentRepository(folder.resolve("payments.txt")));
        payments.pay(staff, "member", List.of(lostCost.chargeId()), money("450"), PaymentMethod.CASH);
        Loan found = returns.receive(staff, lostLoan.loanId(), ReturnCondition.GOOD, money("0"));
        check(found.status() == LoanStatus.RETURNED);
        check(charges.refundDue("member").equals(money("450")));
        Book restored = books.findAll().stream().filter(item -> item.bookId().equals(lostBook.bookId()))
                .findFirst().orElseThrow();
        check(restored.totalCopies() == 2 && restored.availableCopies() == 2);
        Charge refund = charges.forMember("member").stream().filter(charge -> charge.type() == ChargeType.REFUND)
                .findFirst().orElseThrow();
        payments.recordRefund(user(User.Role.ADMIN, "admin"), "member", refund.chargeId(), money("450"),
                PaymentMethod.UPI);
        check(charges.refundDue("member").signum() == 0);

        Book unpaidBook = book("book-lost-unpaid", 2, 1);
        Loan unpaidLost = loan("lost-unpaid", unpaidBook.bookId(), today.minusDays(2), today.minusDays(1),
                "base=0.00;rent=DAY:0.00;penalty=2.00:100.00");
        List<Book> currentBooks = new ArrayList<>(books.findAll());
        currentBooks.add(unpaidBook);
        books.saveAll(currentBooks);
        List<Loan> currentLoans = new ArrayList<>(loans.findAll());
        currentLoans.add(unpaidLost);
        loans.saveAll(currentLoans);
        lost.markLost(staff, unpaidLost.loanId());
        returns.receive(staff, unpaidLost.loanId(), ReturnCondition.GOOD, money("0"));
        Charge unpaidLostCost = charges.forMember("member").stream()
                .filter(charge -> charge.loanId().equals(unpaidLost.loanId()) && charge.type() == ChargeType.LOST_BOOK)
                .findFirst().orElseThrow();
        check(unpaidLostCost.status() == ChargeStatus.WAIVED);
        check(charges.refundDue("member").signum() == 0);

        Book lateFoundBook = book("book-lost-late", 1, 1);
        Loan lateLost = new Loan("lost-late", lateFoundBook.bookId(), "Book " + lateFoundBook.bookId(), "member",
                today.minusDays(200), today.minusDays(190), today.minusDays(181), LoanStatus.LOST, money("0"),
                money("0"), money("0"), "", "desk", "system", "base=0.00;rent=DAY:0.00;penalty=0.00:0.00");
        currentBooks = new ArrayList<>(books.findAll());
        currentBooks.add(lateFoundBook);
        books.saveAll(currentBooks);
        currentLoans = new ArrayList<>(loans.findAll());
        currentLoans.add(lateLost);
        loans.saveAll(currentLoans);
        charges.add("member", lateLost.loanId(), ChargeType.LOST_BOOK, "Lost book", money("450"));
        charges.add("member", lateLost.loanId(), ChargeType.PROCESSING_FEE, "Lost processing", money("50"));
        returns.receive(staff, lateLost.loanId(), ReturnCondition.GOOD, money("0"));
        check(charges.forMember("member").stream().noneMatch(charge -> charge.loanId().equals(lateLost.loanId())
                && charge.type() == ChargeType.REFUND));
    }

    private static void verifyDuesBlockAndRegistration(Path folder) throws Exception {
        Files.createDirectories(folder);
        CsvBookRepository books = new CsvBookRepository(folder.resolve("books.txt"));
        CsvMemberRepository members = new CsvMemberRepository(folder.resolve("members.txt"));
        CsvLoanRepository loans = new CsvLoanRepository(folder.resolve("loans.txt"));
        CsvChargeRepository chargeRepository = new CsvChargeRepository(folder.resolve("charges.txt"));
        CsvPaymentRepository paymentRepository = new CsvPaymentRepository(folder.resolve("payments.txt"));
        SettingsService settings = new SettingsService(new CsvSettingsRepository(folder.resolve("settings.txt")));
        ChargeService charges = new ChargeService(chargeRepository);
        PaymentService payments = new PaymentService(charges, paymentRepository);
        User admin = user(User.Role.ADMIN, "admin");
        User staff = user(User.Role.STAFF, "staff");
        Book book = book("lending-book", 1, 1);
        books.saveAll(List.of(book));
        MemberService memberService = new MemberService(members, settings, new PermissionPolicy(),
                services.MemberChangeListener.NO_OP);
        memberService.setChargeService(charges);
        Member member = memberService.add(admin, new Member("", "Reader", "1234567", "reader@example.com", "",
                MembershipType.INDIVIDUAL, LocalDate.now(), LocalDate.now().plusDays(365), MemberStatus.ACTIVE,
                null, null));
        Charge registration = charges.forMember(member.memberId()).stream()
                .filter(charge -> charge.type() == ChargeType.MEMBERSHIP_REGISTRATION).findFirst().orElseThrow();
        LendingService lending = new LendingService(books, members, loans, settings,
                new services.RealDuesProvider(charges), new PermissionPolicy(), services.LoanEventListener.NO_OP,
                null, charges);
        try {
            lending.lend(staff, member.memberId(), List.of(new LendingService.LendingRequest(book.bookId(),
                    lending.defaultDueDate())));
            throw new AssertionError("Unpaid registration dues must block lending.");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("unpaid dues"));
        }
        payments.pay(staff, member.memberId(), List.of(registration.chargeId()), registration.balance(),
                PaymentMethod.CASH);
        lending.lend(staff, member.memberId(), List.of(new LendingService.LendingRequest(book.bookId(),
                lending.defaultDueDate())));
        check(lending.activeLoansForMember(member.memberId()).size() == 1);

        services.MembershipService membership = new services.MembershipService(members, charges, settings);
        LocalDate expectedExpiry = member.expiryDate().plusDays(settings.getInt(
                services.SettingsDefaults.key("membership.lengthDays.", member.membershipType())));
        Member renewed = membership.renew(admin, member.memberId());
        check(renewed.expiryDate().equals(expectedExpiry));
        check(charges.forMember(member.memberId()).stream()
                .anyMatch(charge -> charge.type() == ChargeType.MEMBERSHIP_RENEWAL));
        Charge renewalCharge = charges.forMember(member.memberId()).stream()
                .filter(charge -> charge.type() == ChargeType.MEMBERSHIP_RENEWAL).findFirst().orElseThrow();
        try {
            membership.renew(staff, member.memberId());
            throw new AssertionError("Staff cannot renew memberships.");
        } catch (SecurityException expected) { }
        try {
            charges.waive(staff, renewalCharge.chargeId(), renewalCharge.balance(), "reason");
            throw new AssertionError("Staff cannot waive charges.");
        } catch (SecurityException expected) { }
        try {
            charges.addManual(staff, member.memberId(), ChargeType.CARD_REPLACEMENT, "Card", money("50"));
            throw new AssertionError("Staff cannot add manual charges.");
        } catch (SecurityException expected) { }
                charges.add(member.memberId(), "", ChargeType.CARD_REPLACEMENT, "Replacement card", money("25"));
        try {
            membership.renew(admin, member.memberId());
            throw new AssertionError("Unpaid renewal dues block a later renewal.");
        } catch (IllegalArgumentException expected) { }
    }

    private static void verifyAutomaticLostThreshold(Path folder) throws Exception {
        Files.createDirectories(folder);
        LocalDate today = LocalDate.now();
        Book thresholdBook = book("threshold", 1, 0);
        Book beforeBook = book("before-threshold", 1, 0);
        Loan atThreshold = loan("at-threshold", thresholdBook.bookId(), today.minusDays(70),
                today.minusDays(60), "base=0.00;rent=DAY:0.00;penalty=1.00:100.00");
        Loan beforeThreshold = loan("before-threshold", beforeBook.bookId(), today.minusDays(69),
                today.minusDays(59), "base=0.00;rent=DAY:0.00;penalty=1.00:100.00");
        CsvBookRepository books = new CsvBookRepository(folder.resolve("books.txt"));
        CsvLoanRepository loans = new CsvLoanRepository(folder.resolve("loans.txt"));
        CsvChargeRepository chargeRepository = new CsvChargeRepository(folder.resolve("charges.txt"));
        SettingsService settings = new SettingsService(new CsvSettingsRepository(folder.resolve("settings.txt")));
        books.saveAll(List.of(thresholdBook, beforeBook));
        loans.saveAll(List.of(atThreshold, beforeThreshold));
        services.LostBookService lost = new services.LostBookService(books, loans,
                new ChargeService(chargeRepository), settings);
        check(lost.markOverdueLost() == 1);
        check(loans.findAll().stream().filter(loan -> loan.loanId().equals(atThreshold.loanId())).findFirst()
                .orElseThrow().status() == LoanStatus.LOST);
        check(loans.findAll().stream().filter(loan -> loan.loanId().equals(beforeThreshold.loanId())).findFirst()
                .orElseThrow().status() == LoanStatus.ACTIVE);
    }

    private static Book book(String id, int total, int available) {
        return new Book(id, "", "Book " + id, "Author", "Publisher", "2025", "1", "Fiction", "English",
                "A-1", money("450"), total, available, LocalDate.now());
    }

    private static Loan loan(String id, String bookId, LocalDate issue, LocalDate due, String snapshot) {
        return new Loan(id, bookId, "Book " + bookId, "member", issue, due, null, LoanStatus.ACTIVE,
                money("10"), money("0"), money("0"), "", "desk", "", snapshot);
    }

    private static User user(User.Role role, String username) {
        return new User(username, username, username, role, "salt", "hash", true, LocalDateTime.now());
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value).setScale(2);
    }

    private static void check(boolean condition) {
        if (!condition)
            throw new AssertionError("Phase six workflow assertion failed.");
    }
}