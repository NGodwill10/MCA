package data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Notification;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;

/** CSV repository with compatibility for the Phase 1-7 notification columns. */
public final class CsvNotificationRepository implements NotificationRepository {
    private static final List<String> HEADER = List.of("notificationId", "timestamp", "context", "type",
            "severity", "message", "refId", "dedupKey", "dismissed");
    private final Path file;
    private List<Notification> notifications;

    public CsvNotificationRepository(Path file) throws IOException {
        this.file = file;
        notifications = load();
        if (!Files.exists(file) || Files.size(file) == 0) write(notifications);
    }

    @Override public List<Notification> findAll() { return List.copyOf(notifications); }

    @Override public void saveAll(List<Notification> values) throws IOException {
        List<Notification> copy = List.copyOf(values);
        write(copy);
        notifications = copy;
    }

    private List<Notification> load() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        if (records.isEmpty()) return List.of();
        List<String> header = records.getFirst();
        List<Notification> result = new ArrayList<>();
        for (int row = 1; row < records.size(); row++) {
            try {
                List<String> record = records.get(row);
                String type = value(record, header, "type");
                result.add(new Notification(value(record, header, "notificationId"),
                        LocalDateTime.parse(value(record, header, "timestamp")),
                        NotificationContext.valueOf(value(record, header, "context").toUpperCase(Locale.ROOT)),
                        NotificationType.valueOf(type.toUpperCase(Locale.ROOT)),
                        severity(value(record, header, "severity"), type),
                        value(record, header, "message"), value(record, header, "refId"),
                        value(record, header, "dedupKey"), Boolean.parseBoolean(value(record, header, "dismissed"))));
            } catch (RuntimeException exception) {
                System.getLogger(CsvNotificationRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed notification row " + (row + 1) + " in " + file, exception);
            }
        }
        return List.copyOf(result);
    }

    private void write(List<Notification> values) throws IOException {
        CsvUtil.writeAtomically(file, HEADER, values.stream().map(n -> List.of(n.notificationId(),
                n.timestamp().toString(), n.context().name(), n.type().name(), n.severity().name(), n.message(),
                n.refId(), n.dedupKey(), Boolean.toString(n.dismissed()))).toList());
    }

    private static NotificationSeverity severity(String raw, String type) {
        if (!raw.isBlank()) return NotificationSeverity.valueOf(raw.toUpperCase(Locale.ROOT));
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "LOW_STOCK", "MEMBERSHIP_EXPIRING", "RESERVATION_READY" -> NotificationSeverity.WARNING;
            case "BOOK_OVERDUE", "MEMBER_OVERDUE", "MEMBERSHIP_EXPIRED", "BOOK_LOST", "LENDING_BLOCKED" -> NotificationSeverity.ALERT;
            default -> NotificationSeverity.INFO;
        };
    }

    private static String value(List<String> row, List<String> header, String key) {
        int index = header.indexOf(key);
        return index >= 0 && index < row.size() ? row.get(index) : "";
    }
}
