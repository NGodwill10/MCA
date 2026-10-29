package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import model.Notification;
import model.NotificationContext;
import model.NotificationSeverity;
import services.NotificationService;

public final class NotificationPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int PAGE_SIZE = 200;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu");
    private final transient NotificationService notifications;
    private final NotificationContext context;
    private final String menuLabel;
    private final transient Consumer<Notification> viewHandler;
    private final transient BiConsumer<NotificationContext, Integer> badgeHandler;
    private final JComboBox<String> filter = new JComboBox<>(new String[] { "All", "Alerts and warnings", "Info" });
    private final JPanel rows = new JPanel();
    private final JLabel empty = new JLabel("", SwingConstants.CENTER);
    private final JButton loadOlder = new JButton("Load older");
    private final transient List<Notification> loaded = new ArrayList<>();
    private int loadedCount;
    private boolean hasMore;
    private boolean loading;
    private boolean refreshPending;

    public NotificationPanel(NotificationService notifications, NotificationContext context, String menuLabel,
            Consumer<Notification> viewHandler, BiConsumer<NotificationContext, Integer> badgeHandler) {
        this.notifications = Objects.requireNonNull(notifications, "notifications");
        this.context = Objects.requireNonNull(context, "context");
        this.menuLabel = Objects.requireNonNull(menuLabel, "menuLabel");
        this.viewHandler = viewHandler == null ? item -> {
        } : viewHandler;
        this.badgeHandler = badgeHandler == null ? (item, count) -> {
        } : badgeHandler;
        buildPanel();
    }

    public void refresh() {
        if (loading) {
            refreshPending = true;
            return;
        }
        loading = true;
        loadOlder.setEnabled(false);
        new SwingWorker<PanelLoad, Void>() {
            @Override
            protected PanelLoad doInBackground() throws Exception {
                List<Notification> page = notifications.listByContext(context, 0, PAGE_SIZE);
                return new PanelLoad(page, notifications.countUndismissedWarningsAndAlerts(context));
            }

            @Override
            protected void done() {
                try {
                    PanelLoad result = get();
                    loaded.clear();
                    loaded.addAll(result.records());
                    loadedCount = loaded.size();
                    hasMore = loaded.size() == PAGE_SIZE;
                    badgeHandler.accept(context, result.badgeCount());
                    renderRows();
                } catch (Exception exception) {
                    log(exception);
                    showEmpty("Notifications could not be loaded.");
                } finally {
                    loading = false;
                    if (refreshPending) {
                        refreshPending = false;
                        refresh();
                    }
                }
            }
        }.execute();
    }

    private void buildPanel() {
        setLayout(new BorderLayout(0, 6));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createLineBorder(Theme.LINE));
        JPanel controls = new JPanel(new BorderLayout(4, 4));
        controls.setBackground(Theme.WHITE);
        controls.add(filter, BorderLayout.CENTER);
        JButton dismissAll = new JButton("Dismiss all");
        dismissAll.addActionListener(event -> dismissAll());
        controls.add(dismissAll, BorderLayout.EAST);
        add(controls, BorderLayout.NORTH);

        rows.setBackground(Theme.WHITE);
        rows.setLayout(new javax.swing.BoxLayout(rows, javax.swing.BoxLayout.Y_AXIS));
        empty.setForeground(Theme.MUTED);
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.WHITE);
        center.add(rows, BorderLayout.NORTH);
        center.add(empty, BorderLayout.CENTER);
        add(new JScrollPane(center), BorderLayout.CENTER);

        loadOlder.setVisible(false);
        loadOlder.addActionListener(event -> loadOlder());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        footer.setBackground(Theme.WHITE);
        footer.add(loadOlder);
        add(footer, BorderLayout.SOUTH);
        filter.addActionListener(event -> renderRows());
        showEmpty("No notifications for " + menuLabel + " yet.");
    }

    private void loadOlder() {
        if (loading || !hasMore) {
            return;
        }
        loading = true;
        loadOlder.setEnabled(false);
        int offset = loadedCount;
        new SwingWorker<PanelLoad, Void>() {
            @Override
            protected PanelLoad doInBackground() throws Exception {
                List<Notification> page = notifications.listByContext(context, offset, PAGE_SIZE);
                return new PanelLoad(page, notifications.countUndismissedWarningsAndAlerts(context));
            }

            @Override
            protected void done() {
                try {
                    PanelLoad result = get();
                    loaded.addAll(result.records());
                    loadedCount += result.records().size();
                    hasMore = result.records().size() == PAGE_SIZE;
                    badgeHandler.accept(context, result.badgeCount());
                    renderRows();
                } catch (Exception exception) {
                    log(exception);
                } finally {
                    loading = false;
                    if (refreshPending) {
                        refreshPending = false;
                        refresh();
                    } else {
                        loadOlder.setEnabled(hasMore);
                    }
                }
            }
        }.execute();
    }

    private void dismiss(String notificationId) {
        runAction(() -> notifications.dismiss(notificationId));
    }

    private void dismissAll() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Dismiss all notifications for " + menuLabel + "?",
                "Dismiss notifications", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            runAction(() -> notifications.dismissAll(context));
        }
    }

    private void runAction(IoAction action) {
        if (loading) {
            return;
        }
        loading = true;
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                action.run();
                return null;
            }

            @Override
            protected void done() {
                loading = false;
                try {
                    get();
                    refresh();
                } catch (Exception exception) {
                    log(exception);
                }
            }
        }.execute();
    }

    private void renderRows() {
        rows.removeAll();
        List<Notification> visible = loaded.stream().filter(this::matchesFilter).toList();
        for (Notification notification : visible) {
            rows.add(createRow(notification));
            rows.add(javax.swing.Box.createVerticalStrut(4));
        }
        empty.setText(loaded.isEmpty() ? "No notifications for " + menuLabel + " yet."
                : "No notifications match this filter.");
        empty.setVisible(visible.isEmpty());
        rows.revalidate();
        rows.repaint();
        loadOlder.setVisible(hasMore);
        loadOlder.setEnabled(hasMore && !loading);
    }

    private JPanel createRow(Notification notification) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setBackground(Theme.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE),
                BorderFactory.createEmptyBorder(6, 6, 6, 4)));
        JLabel severity = new JLabel(severityIcon(notification.severity()), SwingConstants.CENTER);
        severity.setForeground(severityColor(notification.severity()));
        severity.setToolTipText(notification.severity().name());
        row.add(severity, BorderLayout.WEST);

        JPanel details = new JPanel(new GridLayout(0, 1, 0, 3));
        details.setOpaque(false);
        JTextArea message = new JTextArea(notification.message());
        message.setEditable(false);
        message.setLineWrap(true);
        message.setWrapStyleWord(true);
        message.setOpaque(false);
        message.setFont(Theme.BODY);
        details.add(message);
        JLabel timestamp = new JLabel(relativeTime(notification.timestamp()));
        timestamp.setForeground(Theme.MUTED);
        timestamp.setFont(Theme.BODY);
        details.add(timestamp);
        row.add(details, BorderLayout.CENTER);

        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 2));
        actions.setOpaque(false);
        if (!notification.refId().isBlank()) {
            JButton view = new JButton("View");
            view.setToolTipText("View related record");
            view.addActionListener(event -> viewHandler.accept(notification));
            actions.add(view);
        }
        JButton dismiss = new JButton("\u00D7");
        dismiss.setToolTipText("Dismiss notification");
        dismiss.getAccessibleContext().setAccessibleName("Dismiss notification");
        dismiss.addActionListener(event -> dismiss(notification.notificationId()));
        actions.add(dismiss);
        row.add(actions, BorderLayout.EAST);
        return row;
    }

    private boolean matchesFilter(Notification notification) {
        return switch ((String) filter.getSelectedItem()) {
            case "Alerts and warnings" -> notification.severity() != NotificationSeverity.INFO;
            case "Info" -> notification.severity() == NotificationSeverity.INFO;
            default -> true;
        };
    }

    private static String severityIcon(NotificationSeverity severity) {
        return switch (severity) {
            case INFO -> "i";
            case WARNING -> "!";
            case ALERT -> "!!";
        };
    }

    private static Color severityColor(NotificationSeverity severity) {
        return switch (severity) {
            case INFO -> Theme.ACCENT_DARK;
            case WARNING -> new Color(170, 105, 10);
            case ALERT -> Theme.ERROR;
        };
    }

    private static String relativeTime(LocalDateTime timestamp) {
        LocalDate date = timestamp.toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return "Today " + TIME.format(timestamp);
        }
        if (date.equals(today.minusDays(1))) {
            return "Yesterday " + TIME.format(timestamp);
        }
        return DATE.format(timestamp);
    }

    private void showEmpty(String message) {
        empty.setText(message);
        empty.setVisible(true);
    }

    private static void log(Exception exception) {
        System.getLogger(NotificationPanel.class.getName()).log(System.Logger.Level.ERROR,
                "Notification panel operation failed", exception);
    }

    private record PanelLoad(List<Notification> records, int badgeCount) {
    }

    @FunctionalInterface
    private interface IoAction {
        void run() throws IOException;
    }
}