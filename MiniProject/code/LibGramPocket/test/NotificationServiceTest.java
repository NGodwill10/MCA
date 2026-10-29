import data.CsvNotificationRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import model.Notification;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;
import services.NotificationService;

public final class NotificationServiceTest {
    private NotificationServiceTest() {
    }

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("pocket-notifications-test-");
        try {
            Path file = directory.resolve("notifications.txt");
            Clock clock = Clock.fixed(Instant.parse("2026-10-21T12:30:00Z"), ZoneOffset.UTC);
            CsvNotificationRepository repository = new CsvNotificationRepository(file);
            NotificationService service = new NotificationService(repository, clock);
            verifyContextPagingDismissalAndBadges(service, repository);
            verifyDeduplicationAndPruning(service, repository);
            verifyLargePaging(service, repository);
            verifyCsvRoundTrip(file);
            System.out.println("NotificationServiceTest passed");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static void verifyContextPagingDismissalAndBadges(NotificationService service,
            CsvNotificationRepository repository) throws Exception {
        Notification first = service.add(NotificationContext.LEND, NotificationType.LEND_DUE_SOON,
                NotificationSeverity.WARNING, "Ravi is due soon", "loan-1");
        service.add(NotificationContext.LEND, NotificationType.LEND_OVERDUE, NotificationSeverity.ALERT,
                "Ravi is overdue", "loan-2");
        service.add(NotificationContext.BORROW, NotificationType.BORROW_DUE_SOON, NotificationSeverity.WARNING,
                "Return soon", "loan-3");
        service.add(NotificationContext.LEND, NotificationType.BOOK_LENT, NotificationSeverity.INFO,
                "Lent at 12:30", "loan-4");
        check(service.listByContext(NotificationContext.LEND, 0, 2).size() == 2);
        check(service.listByContext(NotificationContext.LEND, 2, 2).size() == 1);
        check(service.countUndismissedWarningsAndAlerts(NotificationContext.LEND) == 2);
        check(service.countUndismissedWarningsAndAlerts(NotificationContext.BORROW) == 1);
        service.dismiss(first.notificationId());
        check(service.listByContext(NotificationContext.LEND, 0, 10).size() == 2);
        check(service.countUndismissedWarningsAndAlerts(NotificationContext.LEND) == 1);
        check(repository.findAll().stream().anyMatch(item -> item.notificationId().equals(first.notificationId())
                && item.dismissed()));
        check(service.dismissAll(NotificationContext.LEND) == 2);
        check(service.listByContext(NotificationContext.LEND, 0, 10).isEmpty());
        check(service.listByContext(NotificationContext.BORROW, 0, 10).size() == 1);
    }

    private static void verifyDeduplicationAndPruning(NotificationService service,
            CsvNotificationRepository repository) throws Exception {
        String key = "LEND_OVERDUE:loan-5:2026-10-21";
        check(service.addIfAbsent(NotificationContext.LEND, NotificationType.LEND_OVERDUE,
                NotificationSeverity.ALERT, "Overdue today", "loan-5", key).isPresent());
        check(service.addIfAbsent(NotificationContext.LEND, NotificationType.LEND_OVERDUE,
                NotificationSeverity.ALERT, "Overdue today", "loan-5", key).isEmpty());
        Notification oldDismissed = new Notification("old-dismissed", LocalDateTime.parse("2025-01-01T00:00:00"),
                NotificationContext.BOOKKEEPING, NotificationType.BOOK_ADDED, NotificationSeverity.INFO,
                "Old", "book", "", true);
        repository.saveAll(java.util.stream.Stream.concat(repository.findAll().stream(),
                java.util.stream.Stream.of(oldDismissed)).toList());
        check(service.pruneDismissedBefore(LocalDateTime.parse("2025-10-21T00:00:00")) == 1);
        check(repository.findAll().stream().noneMatch(item -> item.notificationId().equals("old-dismissed")));
    }

    private static void verifyCsvRoundTrip(Path file) throws Exception {
        CsvNotificationRepository restarted = new CsvNotificationRepository(file);
        Notification notification = restarted.findAll().stream()
            .filter(item -> item.type() == NotificationType.LEND_OVERDUE)
            .filter(item -> item.refId().equals("loan-5")).findFirst().orElseThrow();
        check(notification.message().equals("Overdue today"));
        check(notification.dedupKey().equals("LEND_OVERDUE:loan-5:2026-10-21"));
        check(notification.timestamp().equals(LocalDateTime.parse("2026-10-21T12:30")));
    }

    private static void verifyLargePaging(NotificationService service, CsvNotificationRepository repository)
            throws Exception {
        List<Notification> records = new java.util.ArrayList<>(repository.findAll());
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        for (int index = 0; index < 205; index++) {
            records.add(new Notification("page-" + index, start.plusSeconds(index), NotificationContext.BOOKKEEPING,
                    NotificationType.BOOK_ADDED, NotificationSeverity.INFO, "Book added " + index,
                    "book-" + index, "", false));
        }
        repository.saveAll(records);
        List<Notification> newest = service.listByContext(NotificationContext.BOOKKEEPING, 0, 200);
        List<Notification> older = service.listByContext(NotificationContext.BOOKKEEPING, 200, 200);
        check(newest.size() == 200 && older.size() == 5);
        check(newest.getFirst().notificationId().equals("page-204"));
        check(older.getFirst().notificationId().equals("page-4"));
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Notification service assertion failed");
        }
    }
}