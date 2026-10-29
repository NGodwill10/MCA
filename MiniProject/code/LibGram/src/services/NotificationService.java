package services;

import data.NotificationRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import model.Notification;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;

/** Owns notification creation, querying and dismissal rules. */
public final class NotificationService {
    public static final int DEFAULT_PAGE_SIZE = 200;
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) { this.repository = Objects.requireNonNull(repository); }

    /** Writes an event entry. Event entries are never de-duplicated. */
    public synchronized Notification addEvent(NotificationContext context, NotificationType type,
            String message, String refId) throws IOException {
        return add(context, type, NotificationSeverity.INFO, message, refId, "");
    }

    /** Writes a scanner entry unless its key already exists for the same calendar day. */
    public synchronized Notification addAlert(NotificationContext context, NotificationType type,
            NotificationSeverity severity, String message, String refId, String dedupKey,
            LocalDate date) throws IOException {
        boolean exists = repository.findAll().stream().anyMatch(n -> n.dedupKey().equals(dedupKey)
                && n.timestamp().toLocalDate().equals(date));
        if (exists) return null;
        return add(context, type, severity, message, refId, dedupKey);
    }

    /** Returns one newest-first page of non-dismissed entries for a context. */
    public List<Notification> list(NotificationContext context, int page, int pageSize) throws IOException {
        if (page < 0 || pageSize < 1) throw new IllegalArgumentException("Page must be non-negative.");
        return repository.findAll().stream().filter(n -> n.context() == context && !n.dismissed())
                .sorted(Comparator.comparing(Notification::timestamp).reversed())
                .skip((long) page * pageSize).limit(pageSize).toList();
    }

    public List<Notification> listByContext(NotificationContext context, int page, int pageSize) throws IOException {
        return list(context, page, pageSize);
    }

    public List<Notification> listByContext(NotificationContext context, int page) throws IOException {
        return list(context, page, DEFAULT_PAGE_SIZE);
    }

    public List<Notification> list(NotificationContext context) throws IOException {
        return list(context, 0, DEFAULT_PAGE_SIZE);
    }

    /** Dismisses one entry while retaining it in storage. */
    public synchronized void dismiss(String notificationId) throws IOException {
        update(notificationId, true, null);
    }

    /** Dismisses every visible entry in one context only. */
    public synchronized void dismissAll(NotificationContext context) throws IOException {
        List<Notification> next = new ArrayList<>();
        for (Notification n : repository.findAll()) next.add(n.context() == context ? n.markDismissed() : n);
        repository.saveAll(next);
    }

    public void dismissAllForContext(NotificationContext context) throws IOException { dismissAll(context); }

    public boolean hasDedupKey(String dedupKey, LocalDate date) throws IOException {
        return repository.findAll().stream().anyMatch(n -> n.dedupKey().equals(dedupKey)
                && n.timestamp().toLocalDate().equals(date));
    }

    public long countUndismissed(NotificationContext context) throws IOException {
        return repository.findAll().stream().filter(n -> n.context() == context && !n.dismissed()
                && n.severity() != NotificationSeverity.INFO).count();
    }

    public long countUndismissedAlerts(NotificationContext context) throws IOException { return countUndismissed(context); }

    /** Removes dismissed entries older than the retention period. */
    public synchronized int pruneDismissedBefore(LocalDateTime cutoff) throws IOException {
        List<Notification> old = repository.findAll();
        List<Notification> next = old.stream().filter(n -> !n.dismissed() || !n.timestamp().isBefore(cutoff)).toList();
        if (next.size() != old.size()) repository.saveAll(next);
        return old.size() - next.size();
    }

    public List<Notification> all() throws IOException { return repository.findAll(); }

    private Notification add(NotificationContext context, NotificationType type, NotificationSeverity severity,
            String message, String refId, String dedupKey) throws IOException {
        Notification notification = new Notification(UUID.randomUUID().toString(), LocalDateTime.now(), context,
                type, severity, message, refId, dedupKey, false);
        List<Notification> values = new ArrayList<>(repository.findAll());
        values.add(notification);
        repository.saveAll(values);
        return notification;
    }

    private void update(String id, boolean dismissed, Notification ignored) throws IOException {
        List<Notification> values = new ArrayList<>(repository.findAll());
        int index = -1;
        for (int i = 0; i < values.size(); i++) if (values.get(i).notificationId().equals(id)) { index = i; break; }
        if (index < 0) throw new IllegalArgumentException("Notification does not exist.");
        Notification old = values.get(index);
        values.set(index, new Notification(old.notificationId(), old.timestamp(), old.context(), old.type(),
                old.severity(), old.message(), old.refId(), old.dedupKey(), dismissed));
        repository.saveAll(values);
    }
}
