import data.CsvBookRepository;
import data.CsvChargeRepository;
import data.CsvLoanRepository;
import data.CsvMemberRepository;
import data.CsvReservationRepository;
import data.CsvSettingsRepository;
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
import model.ReservationStatus;
import model.User;
import services.ChargeService;
import services.ReservationService;
import services.SettingsService;

public final class ReservationServiceTest {
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("libgram-reservations-");
        var books=new CsvBookRepository(dir.resolve("books.txt")); var members=new CsvMemberRepository(dir.resolve("members.txt")); var loans=new CsvLoanRepository(dir.resolve("loans.txt"));
        var settings=new SettingsService(new CsvSettingsRepository(dir.resolve("settings.txt"))); var charges=new ChargeService(new CsvChargeRepository(dir.resolve("charges.txt")));
        Book book=new Book("B","123456789X","Reserved book","Author","Pub","2026","1","Cat","EN","A",money("10"),1,0,LocalDate.now()); books.saveAll(java.util.List.of(book));
        Member a=member("A","A");Member b=member("B","B");members.saveAll(java.util.List.of(a,b));
        User staff=new User("U","staff","Staff",User.Role.STAFF,"s","h",true,LocalDateTime.now());
        ReservationService service=new ReservationService(books,members,loans,new CsvReservationRepository(dir.resolve("reservations.txt")),charges,settings);
        var first=service.reserve(staff,"A","B");var second=service.reserve(staff,"B","B");check(service.queuePosition(first)==1&&service.queuePosition(second)==2,"queue order");
        check(service.lendableCopies("B")==0,"held arithmetic"); books.saveAll(java.util.List.of(new Book("B","123456789X","Reserved book","Author","Pub","2026","1","Cat","EN","A",money("10"),1,1,LocalDate.now())));service.onCopyFreed("B");
        check(service.forMember("A").getFirst().status()==ReservationStatus.READY,"first reservation ready");check(service.lendableCopies("B")==0,"ready copy held");
        service.markCollected("A","B");check(service.forMember("A").getFirst().status()==ReservationStatus.COLLECTED,"reservation collected");
        check(charges.forMember("A").size()==0,"zero reservation fee does not create charge");System.out.println("Reservation service tests passed.");
    }
    private static Member member(String id,String name){return new Member(id,name,"1234567890",name+"@example.com","",MembershipType.INDIVIDUAL,LocalDate.now(),LocalDate.now().plusDays(365),MemberStatus.ACTIVE,LocalDateTime.now(),LocalDateTime.now());}
    private static BigDecimal money(String v){return new BigDecimal(v).setScale(2);}
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
