import data.CsvBookRepository;
import data.CsvMemberRepository;
import data.CsvUserRepository;
import data.CsvSettingsRepository;
import data.CsvLoanRepository;
import data.CsvChargeRepository;
import data.CsvPaymentRepository;
import data.CsvReservationRepository;
import data.CsvNotificationRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import services.AuthService;
import services.AccountService;
import services.LendingService;
import services.CatalogService;
import services.MemberService;
import services.SettingsService;
import services.NotificationService;
import services.AlertScanner;
import ui.AppWindow;
import ui.Theme;

public class Main {
        public static void main(String[] args) {
                SwingUtilities.invokeLater(() -> {
                        data.StorageLock storageLock = null;
                        try {
                                Theme.installNimbus();
                                Path requestedStorage = args.length >= 2 && "--storage".equals(args[0])
                                                ? Path.of(args[1])
                                                : null;
                                Path storage = data.StoragePaths.storageDirectory(requestedStorage);
                                Files.createDirectories(storage);
                                data.AppLog.configure(storage);
                                storageLock = data.StorageLock.acquire(storage);
                                data.StorageLock retainedLock = storageLock;
                                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                                        try {
                                                retainedLock.close();
                                        } catch (IOException exception) {
                                                data.AppLog.warning("Unable to release storage lock", exception);
                                        }
                                }, "libgram-storage-lock-release"));
                                CsvUserRepository users = new CsvUserRepository(storage.resolve("users.txt"));
                                AuthService authService = new AuthService(users);
                                AccountService accountService = new AccountService(users);
                                CsvBookRepository bookRepository = new CsvBookRepository(storage.resolve("books.txt"));
                                CsvMemberRepository memberRepository = new CsvMemberRepository(
                                                storage.resolve("members.txt"));
                                SettingsService settingsService = new SettingsService(
                                                new CsvSettingsRepository(storage.resolve("settings.txt")));
                                CsvLoanRepository loanRepository = new CsvLoanRepository(storage.resolve("loans.txt"));
                                NotificationService notificationService = new NotificationService(
                                                new CsvNotificationRepository(storage.resolve("notifications.txt")));
                                notificationService.pruneDismissedBefore(java.time.LocalDateTime.now().minusDays(365));
                                services.BillingNotificationListener billingEvents = new services.BillingNotificationListener(
                                                notificationService);
                                services.ChargeService chargeService = new services.ChargeService(
                                                new CsvChargeRepository(storage.resolve("charges.txt")),
                                                new services.PermissionPolicy(), billingEvents);
                                services.PaymentService paymentService = new services.PaymentService(chargeService,
                                                new CsvPaymentRepository(storage.resolve("payments.txt")),
                                                billingEvents);
                                CsvReservationRepository reservationRepository = new CsvReservationRepository(
                                                storage.resolve("reservations.txt"));
                                services.ReservationService reservationService = new services.ReservationService(
                                                bookRepository, memberRepository, loanRepository,
                                                reservationRepository, chargeService, settingsService,
                                                new services.PermissionPolicy(),
                                                new services.ReservationNotificationListener(notificationService));
                                reservationService.expireReady();
                                chargeService.migrateActiveLoanBases(loanRepository);
                                services.LostBookService lostBookService = new services.LostBookService(bookRepository,
                                                loanRepository, chargeService,
                                                settingsService,
                                                new services.LostBookNotificationListener(notificationService));
                                lostBookService.markOverdueLost();
                                services.ReturnService returnService = new services.ReturnService(bookRepository,
                                                loanRepository, chargeService, settingsService,
                                                new services.PermissionPolicy(), reservationService,
                                                new services.ReturnNotificationListener(notificationService));
                                CatalogService catalogService = new CatalogService(bookRepository, loanRepository,
                                                reservationRepository,
                                                new services.BookNotificationListener(notificationService));
                                MemberService memberService = new MemberService(memberRepository, settingsService,
                                                new services.PermissionPolicy(),
                                                new services.MemberNotificationListener(notificationService),
                                                loanRepository);
                                memberService.setChargeService(chargeService);
                                memberService.setReservationService(reservationService);
                                services.MembershipService membershipService = new services.MembershipService(
                                                memberRepository,
                                                chargeService, settingsService);
                                LendingService lendingService = new LendingService(bookRepository, memberRepository,
                                                loanRepository,
                                                settingsService, new services.RealDuesProvider(chargeService),
                                                new services.PermissionPolicy(),
                                                new services.LoanNotificationListener(notificationService),
                                                reservationService, chargeService);
                                AlertScanner alertScanner = new AlertScanner(bookRepository, memberRepository,
                                                loanRepository,
                                                settingsService, notificationService,
                                                java.time.Clock.systemDefaultZone(), () -> {
                                                        try {
                                                                reservationService.expireReady();
                                                                lostBookService.markOverdueLost();
                                                        } catch (IOException exception) {
                                                                System.getLogger(Main.class.getName()).log(
                                                                                System.Logger.Level.WARNING,
                                                                                "Maintenance scan failed", exception);
                                                        }
                                                });
                                List<String> recoveryNotices = data.CsvUtil.consumeRecoveryNotices();
                                alertScanner.scanAsync();
                                new AppWindow(authService, accountService, catalogService, memberService,
                                                settingsService,
                                                membershipService, lendingService, chargeService, paymentService,
                                                returnService, lostBookService, reservationService,
                                                notificationService, alertScanner, recoveryNotices).show();
                        } catch (IOException exception) {
                                data.AppLog.error("Unable to initialize LibGram storage", exception);
                                JOptionPane.showMessageDialog(null, exception.getMessage().contains("already running")
                                                ? "LibGram is already running. Close the other copy first."
                                                : "LibGram could not open its local data files.",
                                                "Startup error", JOptionPane.ERROR_MESSAGE);
                        }
                });
        }
}
