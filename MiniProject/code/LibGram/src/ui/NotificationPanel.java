package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import model.Notification;
import model.NotificationContext;
import model.NotificationSeverity;
import services.NotificationService;
import java.util.function.Consumer;

/** Context-sensitive, paged notification log. All file work is done by SwingWorker. */
public final class NotificationPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private final transient NotificationService notifications;
    private final NotificationContext context;
    private final transient Consumer<Notification> onView;
    private final JPanel rows = new JPanel();
    private final JLabel empty = new JLabel("No notifications yet.", SwingConstants.CENTER);
    private final JComboBox<String> filter = new JComboBox<>(new String[] { "All", "Alerts and warnings", "Info" });
    private int page;
    private JButton older;

    public NotificationPanel(NotificationService notifications, NotificationContext context) {
        this(notifications, context, notification -> { });
    }

    public NotificationPanel(NotificationService notifications, NotificationContext context, Consumer<Notification> onView) {
        super(new BorderLayout(0, 6));
        this.notifications = notifications; this.context = context; this.onView = onView;
        setBackground(Theme.WHITE); setBorder(BorderFactory.createLineBorder(Theme.LINE));
        JPanel toolbar = new JPanel(new BorderLayout(4, 0)); toolbar.setOpaque(false);
        toolbar.add(filter, BorderLayout.WEST);
        JButton dismissAll = new JButton("Dismiss all");
        dismissAll.addActionListener(event -> new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { notifications.dismissAll(context); return null; }
            @Override protected void done() { page = 0; refresh(); }
        }.execute());
        toolbar.add(dismissAll, BorderLayout.EAST); add(toolbar, BorderLayout.NORTH);
        rows.setLayout(new GridLayout(0, 1, 0, 4)); rows.setOpaque(false);
        add(new JScrollPane(rows), BorderLayout.CENTER);
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER)); footer.setOpaque(false);
        older = new JButton("Load older"); older.addActionListener(event -> { page++; refresh(); });
        footer.add(older); add(footer, BorderLayout.SOUTH);
        filter.addActionListener(event -> { page = 0; refresh(); });
        refresh();
    }

    public void refresh() {
        new SwingWorker<List<Notification>, Void>() {
            @Override protected List<Notification> doInBackground() throws Exception {
                return notifications.list(context, page, NotificationService.DEFAULT_PAGE_SIZE);
            }
            @Override protected void done() {
                try { render(get()); } catch (Exception exception) {
                    rows.removeAll(); rows.add(new JLabel("Notifications unavailable.", SwingConstants.CENTER));
                    rows.revalidate(); rows.repaint();
                }
            }
        }.execute();
    }

    private void render(List<Notification> values) {
        rows.removeAll(); String selected = (String) filter.getSelectedItem();
        List<Notification> visible = values.stream().filter(n -> "All".equals(selected)
                || ("Info".equals(selected) ? n.severity() == NotificationSeverity.INFO : n.severity() != NotificationSeverity.INFO)).toList();
        for (Notification notification : visible) rows.add(row(notification));
        empty.setText("No notifications for " + title(context) + " yet.");
        empty.setVisible(visible.isEmpty()); if (visible.isEmpty()) rows.add(empty);
        older.setEnabled(values.size() == NotificationService.DEFAULT_PAGE_SIZE);
        rows.revalidate(); rows.repaint();
    }

    private JPanel row(Notification notification) {
        JPanel panel = new JPanel(new BorderLayout(5, 2)); panel.setOpaque(true); panel.setBackground(Theme.WHITE);
        JLabel marker = new JLabel(marker(notification.severity())); marker.setForeground(color(notification.severity()));
        marker.setToolTipText(notification.severity().name()); panel.add(marker, BorderLayout.WEST);
        JLabel message = new JLabel("<html>" + escape(notification.message()) + "<br><small>" + relative(notification.timestamp()) + "</small></html>");
        panel.add(message, BorderLayout.CENTER);
        JButton view = new JButton("View"); view.setToolTipText("Open the related item");
        view.addActionListener(event -> onView.accept(notification));
        panel.add(view, BorderLayout.EAST);
        JButton dismiss = new JButton("×"); dismiss.setToolTipText("Dismiss notification"); dismiss.setMargin(new java.awt.Insets(1, 4, 1, 4));
        dismiss.addActionListener(event -> new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception { notifications.dismiss(notification.notificationId()); return null; }
            @Override protected void done() { refresh(); }
        }.execute()); panel.add(dismiss, BorderLayout.SOUTH);
        return panel;
    }

    private static String relative(LocalDateTime timestamp) {
        LocalDate today = LocalDate.now();
        if (timestamp.toLocalDate().equals(today)) return "Today " + timestamp.format(TIME);
        if (timestamp.toLocalDate().equals(today.minusDays(1))) return "Yesterday " + timestamp.format(TIME);
        return timestamp.format(DATE);
    }
    private static String marker(NotificationSeverity severity) { return switch (severity) { case INFO -> "●"; case WARNING -> "▲"; case ALERT -> "◆"; }; }
    private static Color color(NotificationSeverity severity) { return switch (severity) { case INFO -> new Color(45, 95, 150); case WARNING -> new Color(180, 115, 0); case ALERT -> new Color(170, 35, 35); }; }
    private static String title(NotificationContext context) { return context.name().toLowerCase().replace('_', ' '); }
    private static String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
}
