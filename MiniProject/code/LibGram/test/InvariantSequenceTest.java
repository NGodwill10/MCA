import data.CsvBookRepository;
import data.CsvChargeRepository;
import data.CsvLoanRepository;
import data.CsvMemberRepository;
import data.CsvReservationRepository;
import data.CsvSettingsRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import model.Book;
import model.Loan;
import model.LoanStatus;
import model.ReservationStatus;
import model.ReturnCondition;
import services.ChargeService;
import services.LendingService;
import services.LostBookService;
import services.PermissionPolicy;
import services.ReservationService;
import services.ReturnService;
import services.SettingsService;

public final class InvariantSequenceTest {
    private InvariantSequenceTest() { }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("libgram-invariant-");
        try {
            CsvBookRepository books = new CsvBookRepository(folder.resolve("books.txt"));
            CsvMemberRepository members = new CsvMemberRepository(folder.resolve("members.txt"));
            CsvLoanRepository loans = new CsvLoanRepository(folder.resolve("loans.txt"));
            CsvReservationRepository reservations = new CsvReservationRepository(folder.resolve("reservations.txt"));
            SettingsService settings = new SettingsService(new CsvSettingsRepository(folder.resolve("settings.txt")));
            ChargeService charges = new ChargeService(new CsvChargeRepository(folder.resolve("charges.txt")));
            members.saveAll(List.of(TestSupport.member("A", "Member A"), TestSupport.member("B", "Member B")));
            Book reservedBook = TestSupport.book("reserved", 1, 1);
            Book lostBook = TestSupport.book("lost", 1, 0);
            Book damagedBook = TestSupport.book("damaged", 1, 0);
            books.saveAll(List.of(reservedBook, lostBook, damagedBook));
            LocalDate today = LocalDate.now();
            loans.saveAll(List.of(TestSupport.loan("lost-loan", "lost", "A", today, today.plusDays(7), LoanStatus.ACTIVE),
                    TestSupport.loan("damaged-loan", "damaged", "A", today.minusDays(2), today.plusDays(5), LoanStatus.ACTIVE)));

            ReservationService reservationService = new ReservationService(books, members, loans, reservations, charges,
                    settings);
            LendingService lending = new LendingService(books, members, loans, settings,
                    new services.RealDuesProvider(charges), new PermissionPolicy(), services.LoanEventListener.NO_OP,
                    reservationService, charges);
            checkInvariants(books, loans, charges);

            Loan reservedLoan = lending.lend(TestSupport.user(model.User.Role.STAFF, true), "A",
                    List.of(new LendingService.LendingRequest("reserved", lending.defaultDueDate()))).getFirst();
            checkInvariants(books, loans, charges);
            reservationService.reserve(TestSupport.user(model.User.Role.STAFF, true), "B", "reserved");
            checkInvariants(books, loans, charges);

            ReturnService returns = new ReturnService(books, loans, charges, settings,
                    new PermissionPolicy(), reservationService);
            returns.receive(TestSupport.user(model.User.Role.STAFF, true), reservedLoan.loanId(), ReturnCondition.GOOD,
                    TestSupport.money("0"));
            TestSupport.check(reservationService.forMember("B").getFirst().status() == ReservationStatus.READY,
                    "Returned copy promotes the reservation queue");
            checkInvariants(books, loans, charges);
            reservationService.cancel(TestSupport.user(model.User.Role.STAFF, true),
                    reservationService.forMember("B").getFirst().reservationId());
            checkInvariants(books, loans, charges);

            LostBookService lost = new LostBookService(books, loans, charges, settings);
            lost.markLost(TestSupport.user(model.User.Role.STAFF, true), "lost-loan");
            checkInvariants(books, loans, charges);
            returns.receive(TestSupport.user(model.User.Role.STAFF, true), "damaged-loan", ReturnCondition.SEVERE_DAMAGE,
                    TestSupport.money("0"));
            checkInvariants(books, loans, charges);
            System.out.println("Invariant sequence tests passed.");
        } finally {
            TestSupport.deleteTree(folder);
        }
    }

    private static void checkInvariants(CsvBookRepository books, CsvLoanRepository loans, ChargeService charges)
            throws Exception {
        for (Book book : books.findAll()) {
            long active = loans.findAll().stream().filter(loan -> loan.bookId().equals(book.bookId())
                    && loan.status() == LoanStatus.ACTIVE).count();
            TestSupport.equals(Long.valueOf(book.totalCopies() - active), Long.valueOf(book.availableCopies()),
                    "Inventory invariant for " + book.bookId());
        }
        for (var memberId : List.of("A", "B")) {
            charges.forMember(memberId).forEach(charge -> TestSupport.equals(
                    charge.amount().subtract(charge.waivedAmount()).subtract(charge.paidAmount()).setScale(2),
                    charge.balance(), "Charge balance invariant for " + charge.chargeId()));
        }
    }
}
