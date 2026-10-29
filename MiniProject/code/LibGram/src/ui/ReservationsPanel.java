package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import model.Book;
import model.Member;
import model.Reservation;
import model.ReservationStatus;
import model.User;
import services.CatalogService;
import services.MemberService;
import services.ReservationService;

public final class ReservationsPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient User actor;
    private final transient ReservationService reservations;
    private final transient MemberService members;
    private final transient CatalogService catalog;
    private final JTable table = new JTable();
    private final JLabel count = new JLabel();
    private final JComboBox<String> status = new JComboBox<>(new String[] { "All", "Ready", "Pending", "Closed" });
    private final JTextField search = new JTextField(18);
    private transient List<Reservation> visible = List.of();

    public ReservationsPanel(User actor, ReservationService reservations, MemberService members,
            CatalogService catalog) {
        this.actor = actor;
        this.reservations = reservations;
        this.members = members;
        this.catalog = catalog;
        build();
    }

    private void build() {
        setLayout(new BorderLayout(8, 8));
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton add = new JButton("New reservation");
        add.addActionListener(event -> create());
        JButton cancel = new JButton("Cancel reservation");
        cancel.addActionListener(event -> cancel());
        controls.add(add);
        controls.add(cancel);
        controls.add(new JLabel("Status:"));
        controls.add(status);
        controls.add(new JLabel("Search:"));
        controls.add(search);
        status.addActionListener(event -> refresh());
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { refresh(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { refresh(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { refresh(); }
        });
        add(controls, BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(count, BorderLayout.SOUTH);
        refresh();
    }

    public void refresh() {
        try {
            Map<String, String> memberNames = members.listSorted(actor).stream()
                    .collect(Collectors.toMap(Member::memberId, Member::name));
            Map<String, String> bookTitles = catalog.loadBooks().stream()
                    .collect(Collectors.toMap(Book::bookId, Book::title));
            String query = search.getText().trim().toLowerCase(Locale.ROOT);
            String selectedStatus = (String) status.getSelectedItem();
            visible = reservations.list().stream()
                    .filter(reservation -> matchesStatus(reservation, selectedStatus))
                    .filter(reservation -> query.isEmpty()
                            || contains(memberNames.getOrDefault(reservation.memberId(), reservation.memberId()), query)
                            || contains(bookTitles.getOrDefault(reservation.bookId(), reservation.bookTitleSnapshot()), query))
                    .toList();
            DefaultTableModel model = new DefaultTableModel(
                    new String[] { "Member", "Book", "Reserved at", "Status", "Queue position", "Expires" }, 0) {
                private static final long serialVersionUID = 1L;
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            for (Reservation reservation : visible) {
                model.addRow(new Object[] {
                        memberNames.getOrDefault(reservation.memberId(), reservation.memberId()),
                        bookTitles.getOrDefault(reservation.bookId(), reservation.bookTitleSnapshot()),
                        reservation.reservedAt(), reservation.status(), reservations.queuePosition(reservation),
                        reservation.expiresAt() == null ? "" : reservation.expiresAt()
                });
            }
            table.setModel(model);
            count.setText(visible.size() + " reservation(s)");
        } catch (IOException exception) {
            visible = List.of();
            table.setModel(new DefaultTableModel(new String[] { "Reservations" }, 0));
            count.setText("Reservations unavailable: " + exception.getMessage());
        }
    }

    private void create() {
        try {
            List<Member> memberOptions = members.listSorted(actor);
            Member member = (Member) JOptionPane.showInputDialog(this, "Member", "New reservation",
                    JOptionPane.PLAIN_MESSAGE, null, memberOptions.toArray(), memberOptions.isEmpty() ? null : memberOptions.getFirst());
            if (member == null) return;
            List<Book> bookOptions = catalog.loadBooks().stream()
                    .filter(book -> reservations.lendableCopies(book.bookId()) == 0)
                    .toList();
            Book book = (Book) JOptionPane.showInputDialog(this, "Book", "New reservation",
                    JOptionPane.PLAIN_MESSAGE, null, bookOptions.toArray(), bookOptions.isEmpty() ? null : bookOptions.getFirst());
            if (book == null) return;
            reservations.reserve(actor, member.memberId(), book.bookId());
            refresh();
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private void cancel() {
        int row = table.getSelectedRow();
        if (row < 0) {
            showError("Select a pending or ready reservation first.");
            return;
        }
        Reservation reservation = visible.get(table.convertRowIndexToModel(row));
        if (JOptionPane.showConfirmDialog(this, "Cancel this reservation?", "Confirm cancellation",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            reservations.cancel(actor, reservation.reservationId());
            refresh();
        } catch (Exception exception) {
            showError(exception.getMessage());
        }
    }

    private static boolean matchesStatus(Reservation reservation, String selected) {
        return "All".equals(selected)
                || "Ready".equals(selected) && reservation.status() == ReservationStatus.READY
                || "Pending".equals(selected) && reservation.status() == ReservationStatus.PENDING
                || "Closed".equals(selected) && reservation.status() != ReservationStatus.READY
                        && reservation.status() != ReservationStatus.PENDING;
    }

    private static boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message == null ? "The reservation could not be completed." : message,
                "Reservation", JOptionPane.ERROR_MESSAGE);
    }
}
