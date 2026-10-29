package ui;

import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.io.IOException;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import model.User;
import services.AuthService;
import services.AccountService;
import services.LendingService;
import services.CatalogService;
import services.MemberService;
import services.SettingsService;
import services.MembershipService;
import services.ChargeService;
import services.PaymentService;
import services.ReturnService;
import services.LostBookService;
import services.ReservationService;
import services.NotificationService;
import services.AlertScanner;

public final class AppWindow {
    private final JFrame frame = new JFrame("LibGram");
    private final AuthService authService;
    private final AccountService accountService;
    private final CatalogService catalogService;
    private final MemberService memberService;
    private final SettingsService settingsService;
    private final MembershipService membershipService;
    private final LendingService lendingService;

    private final ChargeService chargeService;
    private final PaymentService paymentService;
    private final ReturnService returnService;
    private final LostBookService lostBookService;
    private final ReservationService reservationService;
    private final NotificationService notificationService;
    private final AlertScanner alertScanner;
    private final javax.swing.Timer alertTimer;
    private final List<String> recoveryNotices;

    public AppWindow(AuthService authService, AccountService accountService, CatalogService catalogService,
            MemberService memberService,
            SettingsService settingsService, MembershipService membershipService, LendingService lendingService,
            ChargeService chargeService, PaymentService paymentService,
            ReturnService returnService, LostBookService lostBookService, ReservationService reservationService,
            NotificationService notificationService, AlertScanner alertScanner, List<String> recoveryNotices) {
        this.authService = authService;
        this.accountService = accountService;
        this.catalogService = catalogService;
        this.memberService = memberService;
        this.settingsService = settingsService;
        this.membershipService = membershipService;
        this.lendingService = lendingService;
        this.chargeService = chargeService;
        this.paymentService = paymentService;
        this.returnService = returnService;
        this.lostBookService = lostBookService;
        this.reservationService = reservationService;
        this.notificationService = notificationService;
        this.alertScanner = alertScanner;
        this.recoveryNotices = List.copyOf(recoveryNotices);
        this.alertTimer = new javax.swing.Timer(AlertScanner.TIMER_INTERVAL_MILLIS, event -> alertScanner.scanAsync());
        alertTimer.setRepeats(true);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent event) {
                alertTimer.stop();
                alertScanner.close();
                frame.dispose();
                System.exit(0);
            }
        });
        frame.setMinimumSize(new Dimension(980, 620));
        showLogin();
    }

    public void show() {
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setVisible(true);
    }

    private void showLogin() {
        alertTimer.stop();
        frame.setContentPane(new LoginPanel(authService, this::showMain));
        frame.revalidate();
        frame.repaint();
    }

    private void showMain(User user) {
        try {
            MainFrame main = new MainFrame(frame, user, catalogService, memberService, settingsService,
                    accountService, membershipService, lendingService, chargeService, paymentService, returnService,
                    lostBookService,
                    reservationService, this::showLogin, notificationService, alertScanner);
            frame.setContentPane(main);
            frame.revalidate();
            frame.repaint();
            alertScanner.scanAsync();
            alertTimer.start();
            int repaired = user.role() == model.User.Role.ADMIN ? lendingService.consumeInventoryRepairCount() : 0;
            if (repaired > 0) {
                JOptionPane.showMessageDialog(frame,
                        "Inventory counts for " + repaired + " books were corrected.",
                        "Inventory corrected", JOptionPane.INFORMATION_MESSAGE);
            }
            if (user.role() == model.User.Role.ADMIN && !recoveryNotices.isEmpty()) {
                JOptionPane.showMessageDialog(frame, String.join("\n", recoveryNotices),
                        "Storage recovery", JOptionPane.WARNING_MESSAGE);
            }
        } catch (IOException exception) {
            System.getLogger(AppWindow.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to load catalog data", exception);
            JOptionPane.showMessageDialog(frame, "The catalog data could not be loaded.",
                    "Data error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
