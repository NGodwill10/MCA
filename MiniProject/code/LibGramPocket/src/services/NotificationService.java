package services;

import data.NotificationRepository;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import model.Notification;
import model.NotificationContext;
import model.NotificationSeverity;
import model.NotificationType;

public final class NotificationService implements AutoCloseable {
    private final NotificationRepository repository;
    private final Clock clock;
    private final ExecutorService eventWriter = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "libgram-notification-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final List<Consumer<NotificationContext>> changeListeners = new ArrayList<>();

    public NotificationService(NotificationRepository repository) {
        this(repository, Clock.systemDefaultZone());
    }

    public NotificationService(NotificationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Adds an event notification without a scanner de-duplication key. */
    public Notification add(NotificationContext context, NotificationType type, NotificationSeverity severity,
            String message, String refId) throws IOException {
        return add(context, type, severity, message, refId, "");
    }

    /** Queues an event notification so its CSV write never blocks the Swing event thread. */
    public CompletableFuture<Notification> addEvent(NotificationContext context, NotificationType type,
            NotificationSeverity severity, String message, String refId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return add(context, type, severity, message, refId);
            } catch (IOException exception) {
                throw new CompletionException(exception);
            }
        }, eventWriter);
    }

    /** Waits for event writes already queued on this account's writer. */
    public CompletableFuture<Void> flushEventWrites() {
        return CompletableFuture.runAsync(() -> { }, eventWriter);
    }

    public synchronized void addChangeListener(Consumer<NotificationContext> listener) {
        changeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public synchronized void removeChangeListener(Consumer<NotificationContext> listener) {
        changeListeners.remove(listener);
    }

    /** Adds a scanner notification unless its key already exists for this account. */
    public synchronized Optional<Notification> addIfAbsent(NotificationContext context, NotificationType type,
            NotificationSeverity severity, String message, String refId, String dedupKey) throws IOException {
        Objects.requireNonNull(dedupKey, "dedupKey");
        if (dedupKey.isBlank()) {
            throw new IllegalArgumentException("A scanner notification requires a de-duplication key.");
        }
        if (repository.findAll().stream().anyMatch(item -> item.dedupKey().equals(dedupKey))) {
            return Optional.empty();
        }
        return Optional.of(add(context, type, severity, message, refId, dedupKey));
    }

    /** Lists undismissed notifications newest first, starting at the requested offset. */
    public synchronized List<Notification> listByContext(NotificationContext context, int offset, int pageSize)
            throws IOException {
        Objects.requireNonNull(context, "context");
        if (offset < 0 || pageSize < 1) {
            throw new IllegalArgumentException("Notification page offset must be non-negative and size positive.");
        }
        List<Notification> visible = repository.findAll().stream()
                .filter(item -> item.context() == context && !item.dismissed())
                .sorted(Comparator.comparing(Notification::timestamp).reversed())
                .toList();
        if (offset >= visible.size()) {
            return List.of();
        }
        return visible.subList(offset, Math.min(visible.size(), offset + pageSize));
    }

    /** Dismisses one notification while retaining its record in storage. */
    public synchronized void dismiss(String notificationId) throws IOException {
        List<Notification> records = new ArrayList<>(repository.findAll());
        int index = indexOf(records, notificationId);
        if (index < 0) {
            throw new IllegalArgumentException("Notification not found.");
        }
        Notification existing = records.get(index);
        if (!existing.dismissed()) {
            records.set(index, dismissed(existing));
            repository.saveAll(records);
            notifyChanged(existing.context());
        }
    }

    /** Dismisses all notifications in one context and returns the number changed. */
    public synchronized int dismissAll(NotificationContext context) throws IOException {
        List<Notification> records = new ArrayList<>(repository.findAll());
        int changed = 0;
        for (int index = 0; index < records.size(); index++) {
            Notification existing = records.get(index);
            if (existing.context() == context && !existing.dismissed()) {
                records.set(index, dismissed(existing));
                changed++;
            }
        }
        if (changed > 0) {
            repository.saveAll(records);
            notifyChanged(context);
        }
        return changed;
    }

    /** Counts undismissed Warning and Alert notifications for a menu badge. */
    public synchronized int countUndismissedWarningsAndAlerts(NotificationContext context) throws IOException {
        return (int) repository.findAll().stream().filter(item -> item.context() == context && !item.dismissed())
                .filter(item -> item.severity() != NotificationSeverity.INFO).count();
    }

    /** Removes dismissed records older than the supplied timestamp. */
    public synchronized int pruneDismissedBefore(LocalDateTime cutoff) throws IOException {
        List<Notification> records = repository.findAll();
        List<Notification> retained = records.stream()
                .filter(item -> !item.dismissed() || !item.timestamp().isBefore(cutoff)).toList();
        int removed = records.size() - retained.size();
        if (removed > 0) {
            repository.saveAll(retained);
            changeListeners.forEach(listener -> {
                for (NotificationContext context : NotificationContext.values()) {
                    listener.accept(context);
                }
            });
        }
        return removed;
    }

    /** Returns the current time source used by event entries and scanner keys. */
    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

        private synchronized Notification add(NotificationContext context, NotificationType type,
            NotificationSeverity severity,
            String message, String refId, String dedupKey) throws IOException {
        Notification created = new Notification(UUID.randomUUID().toString(), now(), context, type, severity,
                message, refId, dedupKey, false);
        List<Notification> records = new ArrayList<>(repository.findAll());
        records.add(created);
        repository.saveAll(records);
        notifyChanged(context);
        return created;
    }

    private void notifyChanged(NotificationContext context) {
        changeListeners.forEach(listener -> listener.accept(context));
    }

    @Override
    public void close() {
        eventWriter.shutdown();
    }

    private static int indexOf(List<Notification> records, String notificationId) {
        for (int index = 0; index < records.size(); index++) {
            if (records.get(index).notificationId().equals(notificationId)) {
                return index;
            }
        }
        return -1;
    }

    private static Notification dismissed(Notification notification) {
        return new Notification(notification.notificationId(), notification.timestamp(), notification.context(),
                notification.type(), notification.severity(), notification.message(), notification.refId(),
                notification.dedupKey(), true);
    }
}