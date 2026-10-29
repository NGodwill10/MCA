package data;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Notification;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;

public final class CsvNotificationRepository implements NotificationRepository {
    private static final List<String> HEADER = List.of("notificationId", "timestamp", "context", "type", "severity",
            "message", "refId", "dedupKey", "dismissed", "schemaVersion");
    private final Path file;
    private List<Notification> notifications;

    public CsvNotificationRepository(Path file) throws IOException {
        this.file = file;
        notifications = load();
        if (!CsvUtil.hasHeaderColumn(file, "schemaVersion")) {
            write(notifications);
        }
    }

    @Override
    public List<Notification> findAll() {
        return List.copyOf(notifications);
    }

    @Override
    public void saveAll(List<Notification> updatedNotifications) throws IOException {
        List<Notification> copy = List.copyOf(updatedNotifications);
        write(copy);
        notifications = copy;
    }

    private List<Notification> load() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.getFirst();
        List<Notification> loaded = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> row = records.get(rowIndex);
            try {
                loaded.add(new Notification(value(row, header, "notificationId"),
                        LocalDateTime.parse(value(row, header, "timestamp")),
                        enumValue(NotificationContext.class, value(row, header, "context")),
                        enumValue(NotificationType.class, value(row, header, "type")),
                        severity(value(row, header, "severity")), value(row, header, "message"),
                        value(row, header, "refId"), value(row, header, "dedupKey"),
                        Boolean.parseBoolean(value(row, header, "dismissed"))));
            } catch (RuntimeException exception) {
                System.getLogger(CsvNotificationRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed notification row " + (rowIndex + 1) + " in " + file, exception);
            }
        }
        return List.copyOf(loaded);
    }

    private void write(List<Notification> records) throws IOException {
        List<List<String>> rows = records.stream().map(notification -> List.of(notification.notificationId(),
                notification.timestamp().toString(), notification.context().name(), notification.type().name(),
                notification.severity().name(), notification.message(), notification.refId(), notification.dedupKey(),
                Boolean.toString(notification.dismissed()), CsvUtil.SCHEMA_VERSION)).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }

    private static String value(List<String> row, List<String> header, String name) {
        int index = header.indexOf(name);
        return index < 0 || index >= row.size() ? "" : row.get(index);
    }

    private static NotificationSeverity severity(String value) {
        return value.isBlank() ? NotificationSeverity.INFO : enumValue(NotificationSeverity.class, value);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
    }
}