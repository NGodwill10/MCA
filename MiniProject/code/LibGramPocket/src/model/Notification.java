package model;

import java.time.LocalDateTime;
import java.util.Objects;

public record Notification(String notificationId, LocalDateTime timestamp, NotificationContext context,
        NotificationType type, NotificationSeverity severity, String message, String refId, String dedupKey,
        boolean dismissed) {
    public Notification {
        notificationId = Objects.requireNonNull(notificationId, "notificationId");
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        context = Objects.requireNonNull(context, "context");
        type = Objects.requireNonNull(type, "type");
        severity = Objects.requireNonNull(severity, "severity");
        message = Objects.requireNonNull(message, "message");
        refId = refId == null ? "" : refId;
        dedupKey = dedupKey == null ? "" : dedupKey;
    }
}