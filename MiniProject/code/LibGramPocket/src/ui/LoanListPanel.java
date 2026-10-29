package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowSorter;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import model.Contact;
import model.Loan;
import model.LoanStatus;
import model.PaymentStatus;
import services.LendingService;
import services.LoanRules;
import services.SettingsService;

public final class LoanListPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final boolean activeView;
    private final boolean borrowedView;
    private final transient LendingService lending;
    private final transient ContactServiceFacade contactNames;
    private final transient SettingsService settings;
    private final JComboBox<String> filter;
    private final JTextField search = new JTextField(18);
    private final JLabel count = new JLabel("0 lends", SwingConstants.TRAILING);
    private final JLabel empty = new JLabel("No lends to show.", SwingConstants.CENTER);
    private final transient LoanTableModel model = new LoanTableModel();
    private final JTable table = new JTable(model);
    private final JButton action = new JButton();
    private transient Consumer<Loan> actionHandler = loan -> {
    };
    private transient Runnable backHandler = () -> {
    };

    public LoanListPanel(boolean activeView, LendingService lending, ContactServiceFacade contactNames,
            SettingsService settings) {
        this(activeView, false, lending, contactNames, settings);
    }

    public LoanListPanel(boolean activeView, boolean borrowedView, LendingService lending,
            ContactServiceFacade contactNames, SettingsService settings) {
        this.activeView = activeView;
        this.borrowedView = borrowedView;
        this.lending = lending;
        this.contactNames = contactNames;
        this.settings = settings;
        model.setLendingService(lending);
        filter = new JComboBox<>(activeView
                ? new String[] { "All", "Overdue", "Due soon" }
                : new String[] { "All", "Owed", "Paid" });
        buildPanel();
        refresh();
    }

    public void setActionHandler(Consumer<Loan> handler) {
        actionHandler = handler == null ? loan -> {
        } : handler;
    }

    public void setBackHandler(Runnable handler) {
        backHandler = handler == null ? () -> {
        } : handler;
    }

    public void refresh() {
        try {
            List<Loan> loans = activeView
                    ? borrowedView ? lending.activeBorrows() : lending.activeLends()
                    : borrowedView ? lending.borrowHistory() : lending.lendHistory();
            List<Loan> visible = loans.stream().filter(this::matchesFilter).filter(this::matchesSearch).toList();
            model.setLoans(visible, contactNames, activeView, borrowedView);
            String noun = borrowedView ? "borrow" : "lend";
            count.setText(visible.size() + " " + noun + (visible.size() == 1 ? "" : "s"));
            empty.setText(loans.isEmpty()
                    ? activeView ? borrowedView ? "No active borrows yet." : "No active lends yet."
                            : borrowedView ? "No returned borrows yet." : "No returned lends yet."
                    : "No " + (borrowedView ? "borrows" : "lends") + " match these filters.");
            empty.setVisible(visible.isEmpty());
            updateActionState();
        } catch (IOException exception) {
            System.getLogger(LoanListPanel.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to load lending records", exception);
            empty.setText((borrowedView ? "Borrows" : "Lends") + " could not be loaded.");
            model.setLoans(List.of(), contactNames, activeView, borrowedView);
        }
    }

    public Loan selectedLoan() {
        int selected = table.getSelectedRow();
        return selected < 0 ? null : model.loanAt(table.convertRowIndexToModel(selected));
    }

    public boolean selectLoan(String loanId) {
        filter.setSelectedItem("All");
        search.setText("");
        refresh();
        int modelRow = model.rowForId(loanId);
        if (modelRow < 0) {
            return false;
        }
        int viewRow = table.convertRowIndexToView(modelRow);
        if (viewRow < 0) {
            return false;
        }
        table.setRowSelectionInterval(viewRow, viewRow);
        table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
        return true;
    }

    private void buildPanel() {
        setLayout(new BorderLayout(0, Theme.GAP));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JButton back = new JButton("Back");
        back.addActionListener(event -> backHandler.run());
        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.setOpaque(false);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.setOpaque(false);
        controls.add(new JLabel("Filter"));
        controls.add(filter);
        controls.add(new JLabel("Search contact or book"));
        controls.add(search);
        top.add(back, BorderLayout.WEST);
        top.add(controls, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);
        if (activeView) {
            table.getRowSorter().setSortKeys(List.of(new RowSorter.SortKey(3, SortOrder.ASCENDING)));
        }
        table.getSelectionModel().addListSelectionListener(event -> updateActionState());
        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(Theme.WHITE);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        empty.setForeground(Theme.MUTED);
        center.add(empty, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        bottom.add(count, BorderLayout.WEST);
        action.setText(activeView ? borrowedView ? "Return to lender" : "Receive a return" : "Mark as paid");
        action.setEnabled(false);
        action.addActionListener(event -> {
            Loan loan = selectedLoan();
            if (loan != null) {
                actionHandler.accept(loan);
            }
        });
        bottom.add(action, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);
        filter.addActionListener(event -> refresh());
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                refresh();
            }
        });
    }

    private boolean matchesFilter(Loan loan) {
        String selected = (String) filter.getSelectedItem();
        if (activeView) {
            return switch (selected) {
                case "Overdue" -> LoanRules.daysOverdue(loan.dueDate(), LocalDate.now()) > 0;
                case "Due soon" -> LoanRules.isDueSoon(loan, LocalDate.now(), settings.getInt("alerts.dueSoonDays"));
                default -> loan.status() == LoanStatus.ACTIVE;
            };
        }
        return switch (selected) {
            case "Owed" -> loan.paymentStatus() == PaymentStatus.OWED;
            case "Paid" -> loan.paymentStatus() == PaymentStatus.PAID;
            default -> true;
        };
    }

    private boolean matchesSearch(Loan loan) {
        String query = search.getText().trim().toLowerCase(java.util.Locale.ROOT);
        return query.isEmpty() || loan.bookTitleSnapshot().toLowerCase(java.util.Locale.ROOT).contains(query)
                || contactNames.name(loan.contactId()).toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    private void updateActionState() {
        Loan selected = selectedLoan();
        boolean enabled = selected != null && (activeView || selected.paymentStatus() == PaymentStatus.OWED);
        action.setEnabled(enabled);
        String noun = borrowedView ? "borrow" : "lend";
        action.setToolTipText(enabled ? action.getText()
                : activeView ? "Select an active " + noun + " first" : "Select a " + noun + " with an amount owed");
    }

    @FunctionalInterface
    public interface ContactServiceFacade {
        String name(String contactId);
    }

    private static final class LoanTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private transient List<Loan> loans = List.of();
        private transient List<String> contactNames = List.of();
        private boolean active;
        private boolean borrowed;
        private transient LendingService lending;

        private void setLoans(List<Loan> loans, ContactServiceFacade contacts, boolean activeView,
                boolean borrowedView) {
            this.loans = new ArrayList<>(loans);
            contactNames = this.loans.stream().map(loan -> contacts.name(loan.contactId())).toList();
            active = activeView;
            borrowed = borrowedView;
            fireTableDataChanged();
        }

        private void setLendingService(LendingService service) {
            lending = service;
        }

        private Loan loanAt(int row) {
            return loans.get(row);
        }

        private int rowForId(String loanId) {
            for (int index = 0; index < loans.size(); index++) {
                if (loans.get(index).loanId().equals(loanId)) {
                    return index;
                }
            }
            return -1;
        }

        @Override
        public int getRowCount() {
            return loans.size();
        }

        @Override
        public int getColumnCount() {
            return active ? 6 : 5;
        }

        @Override
        public String getColumnName(int column) {
            if (active) {
                return new String[] { "Contact", "Book", borrowed ? "Borrowed on" : "Lent on", "Due date",
                        "Days overdue",
                        "Amount so far" }[column];
            }
            return new String[] { "Contact", "Book", borrowed ? "Borrowed on" : "Lent on", "Return date",
                    "Total / payment" }[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Loan loan = loans.get(row);
            if (active) {
                return switch (column) {
                    case 0 -> contactNames.get(row);
                    case 1 -> loan.bookTitleSnapshot();
                    case 2 -> DisplayFormat.date(loan.issueDate());
                    case 3 -> DisplayFormat.date(loan.dueDate());
                    case 4 -> LoanRules.daysOverdue(loan.dueDate(), LocalDate.now());
                    case 5 -> DisplayFormat.money(lending.amountSoFar(loan, LocalDate.now()).total());
                    default -> "";
                };
            }
            return switch (column) {
                case 0 -> contactNames.get(row);
                case 1 -> loan.bookTitleSnapshot();
                case 2 -> DisplayFormat.date(loan.issueDate());
                case 3 -> DisplayFormat.date(loan.returnDate());
                case 4 -> DisplayFormat.money(total(loan)) + " / " + loan.paymentStatus();
                default -> "";
            };
        }

        private static BigDecimal total(Loan loan) {
            return loan.baseCharge().add(loan.rentCharge() == null ? java.math.BigDecimal.ZERO : loan.rentCharge())
                    .add(loan.overduePenalty() == null ? java.math.BigDecimal.ZERO : loan.overduePenalty())
                    .add(loan.damageCharge() == null ? java.math.BigDecimal.ZERO : loan.damageCharge())
                    .setScale(2, java.math.RoundingMode.HALF_UP);
        }
    }
}