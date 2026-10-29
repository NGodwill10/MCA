package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import model.Loan;
import services.LendingService;
import services.LoanRules;

public final class ActiveLoansPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient LendingService lending;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[] { "Member", "Book", "Issue date", "Due date", "Days overdue", "Status" }, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTextField search = new JTextField(18);
    private final JComboBox<String> filter = new JComboBox<>(new String[] { "All", "Overdue", "Due soon" });
    private final JLabel count = new JLabel();

    public ActiveLoansPanel(LendingService lending) {
        this.lending = lending; setLayout(new BorderLayout(8, 8)); setBackground(Theme.WHITE);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT)); top.setBackground(Theme.PAPER);
        top.add(new JLabel("Filter:")); top.add(filter); top.add(new JLabel("Search:")); top.add(search);
        add(top, BorderLayout.NORTH);
        JTable table = new JTable(model); table.setFillsViewportHeight(true); table.setAutoCreateRowSorter(true);
        add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false); bottom.add(count, BorderLayout.WEST);
        add(bottom, BorderLayout.SOUTH);
        Timer timer = new Timer(250, event -> refresh()); timer.setRepeats(false);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent event) { timer.restart(); }
            public void removeUpdate(javax.swing.event.DocumentEvent event) { timer.restart(); }
            public void changedUpdate(javax.swing.event.DocumentEvent event) { timer.restart(); }
        });
        filter.addActionListener(event -> refresh()); refresh();
    }

    public void refresh() {
        String query = search.getText().trim().toLowerCase(); String selectedFilter = (String) filter.getSelectedItem();
        List<Loan> visible = lending.activeLoans().stream().filter(loan -> {
            long overdue = LoanRules.daysOverdue(loan, LocalDate.now());
            if ("Overdue".equals(selectedFilter) && overdue == 0) return false;
            if ("Due soon".equals(selectedFilter) && !LoanRules.dueSoon(loan, LocalDate.now())) return false;
            return query.isEmpty() || loan.memberId().toLowerCase().contains(query)
                    || loan.bookTitleSnapshot().toLowerCase().contains(query);
        }).toList();
        model.setRowCount(0); visible.forEach(loan -> model.addRow(new Object[] { loan.memberId(), loan.bookTitleSnapshot(),
                loan.issueDate(), loan.dueDate(), LoanRules.daysOverdue(loan, LocalDate.now()) == 0 ? "" : LoanRules.daysOverdue(loan, LocalDate.now()), loan.status() }));
        count.setText(visible.size() + " active loan(s)");
    }
}
