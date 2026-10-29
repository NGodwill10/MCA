package model;

import java.time.LocalDateTime;

/** A persisted activity-log entry or scanner alert. */
public record Notification(String notificationId, LocalDateTime timestamp,
        NotificationContext context, NotificationType type, NotificationSeverity severity,
        String message, String refId, String dedupKey, boolean dismissed) {
    public Notification {
        if (notificationId == null || notificationId.isBlank() || timestamp == null || context == null
                || type == null || severity == null || message == null) {
            throw new IllegalArgumentException("A notification must have an id, time, type and message.");
        }
        message = message.trim();
        refId = refId == null ? "" : refId;
        dedupKey = dedupKey == null ? "" : dedupKey;
    }
    public Notification markDismissed() { return new Notification(notificationId, timestamp, context, type,
            severity, message, refId, dedupKey, true); }
}
