package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import model.Book;
import model.Charge;
import model.ChargeType;
import model.Loan;
import model.LoanStatus;
import model.Member;
import model.PaymentMethod;
import model.ReturnCondition;
import model.User;
import services.CatalogService;
import services.ChargeCalculator;
import services.ChargeService;
import services.LendingService;
import services.LostBookService;
import services.MemberService;
import services.PaymentService;
import services.ReturnService;
import services.SettingsService;

public final class RecoveryPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private enum Mode {
        RETURNS, LOST, OVERDUE
    }

    private final transient User actor;
    private final transient MemberService members;
    private final transient CatalogService catalog;
    private final transient LendingService lending;
    private final transient ReturnService returns;
    private final transient LostBookService lost;
    private final transient ChargeService charges;
    private final transient PaymentService payments;
    private final transient SettingsService settings;
    private final JComboBox<Member> member = new JComboBox<>();
    private final JTextField memberSearch = new JTextField(16);
    private final JCheckBox includeLost = new JCheckBox("Lost items");
    private final JCheckBox collectNow = new JCheckBox("Collect now");
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[] { "Return", "Member", "Book", "Due date", "Days overdue", "Condition", "Damage (Rs.)",
                    "Overdue fine" },
            0) {
        private static final long serialVersionUID = 1L;

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 || column == 5 || column == 6;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            return column == 0 ? Boolean.class : Object.class;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final DefaultTableModel activeLoansModel = new DefaultTableModel(
            new String[] { "Book", "Member", "Member ID", "Due date", "Status" }, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable activeLoansTable = new JTable(activeLoansModel);
    private final JLabel summary = new JLabel(" ");
    private final transient List<Loan> rows = new ArrayList<>();
    private final transient Map<String, Book> booksById = new HashMap<>();
    private Mode mode = Mode.RETURNS;
    private String loanSearch = "";

    public RecoveryPanel(User actor, MemberService members, CatalogService catalog, LendingService lending,
            ReturnService returns, LostBookService lost, ChargeService charges, PaymentService payments,
            SettingsService settings) throws java.io.IOException {
        this.actor = actor;
        this.members = members;
        this.catalog = catalog;
        this.lending = lending;
        this.returns = returns;
        this.lost = lost;
        this.charges = charges;
        this.payments = payments;
        this.settings = settings;
        build();
    }

    private void build() throws java.io.IOException {
        setLayout(new BorderLayout(8, 8));
        setBackground(Theme.WHITE);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        top.add(new JLabel("Member"));
        memberSearch.setName("recoveryMemberSearch");
        memberSearch.getAccessibleContext().setAccessibleName("Search recovery members");
        top.add(new JLabel("Search members"));
        top.add(memberSearch);
        member.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                String label = value instanceof Member m ? m.name() + "  |  " + m.phone() : "Choose a member";
                return super.getListCellRendererComponent(list, label, index, selected, focus);
            }
        });
        for (Member item : members.listSorted(actor))
            member.addItem(item);
        member.addActionListener(event -> refreshRows());
        memberSearch.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() { refreshMemberChoices(); }
            @Override public void insertUpdate(DocumentEvent event) { changed(); }
            @Override public void removeUpdate(DocumentEvent event) { changed(); }
            @Override public void changedUpdate(DocumentEvent event) { changed(); }
        });
        top.add(member);
        includeLost.addActionListener(event -> refreshRows());
        top.add(includeLost);
        top.add(collectNow);
        JButton confirm = new JButton("Confirm return");
        confirm.addActionListener(event -> receiveSelected());
        top.add(confirm);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getColumnModel().getColumn(5).setCellEditor(new DefaultCellEditor(
                new JComboBox<>(ReturnCondition.values())));
        tableModel.addTableModelListener(event -> {
            if (event.getColumn() == 5 && event.getFirstRow() >= 0 && event.getFirstRow() < rows.size()
                    && tableModel.getValueAt(event.getFirstRow(), 5) == ReturnCondition.SEVERE_DAMAGE) {
                Book book = booksById.get(rows.get(event.getFirstRow()).bookId());
                if (book != null) {
                    tableModel.setValueAt(book.price().add(settings.getMoney("fee.damagedProcessing"))
                            .setScale(2).toPlainString(), event.getFirstRow(), 6);
                }
            }
            refreshBill();
        });
        add(top, BorderLayout.NORTH);
        activeLoansTable.setFillsViewportHeight(true);
        activeLoansTable.setAutoCreateRowSorter(true);
        JPanel activeLoansPane = new JPanel(new BorderLayout(0, 4));
        activeLoansPane.setBackground(Theme.WHITE);
        activeLoansPane.add(new JLabel("Currently lent books"), BorderLayout.NORTH);
        activeLoansPane.add(new JScrollPane(activeLoansTable), BorderLayout.CENTER);
        JPanel actionPane = new JPanel(new BorderLayout());
        actionPane.setBackground(Theme.WHITE);
        actionPane.add(new JScrollPane(table), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, activeLoansPane, actionPane);
        split.setResizeWeight(0.32);
        split.setPreferredSize(new Dimension(700, 500));
        add(split, BorderLayout.CENTER);
        summary.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
        add(summary, BorderLayout.SOUTH);
        refreshRows();
    }

    public void setMode(String action) {
        mode = "Mark lost".equals(action) ? Mode.LOST
                : "Overdue list".equals(action) ? Mode.OVERDUE : Mode.RETURNS;
        refreshRows();
    }

    public void refreshMembers() {
        refreshMemberChoices();
        refreshRows();
    }

    private void refreshMemberChoices() {
        String selectedId = member.getSelectedItem() instanceof Member selected ? selected.memberId() : null;
        String needle = memberSearch.getText().trim().toLowerCase(java.util.Locale.ROOT);
        try {
            member.removeAllItems();
            for (Member item : members.listSorted(actor)) {
                String text = (item.name() + " " + item.memberId() + " " + item.phone() + " " + item.email())
                        .toLowerCase(java.util.Locale.ROOT);
                if (needle.isBlank() || text.contains(needle)) member.addItem(item);
            }
            if (selectedId != null) {
                for (int index = 0; index < member.getItemCount(); index++) {
                    if (member.getItemAt(index).memberId().equals(selectedId)) {
                        member.setSelectedIndex(index);
                        break;
                    }
                }
            }
        } catch (java.io.IOException exception) {
            showError("Members could not be loaded.", "Recover a book");
        }
    }

    public void refreshRows() {
        rows.clear();
        tableModel.setRowCount(0);
        booksById.clear();
        Map<String, Member> membersById = new HashMap<>();
        try {
            catalog.loadBooks().forEach(book -> booksById.put(book.bookId(), book));
            members.listSorted(actor).forEach(item -> membersById.put(item.memberId(), item));
        } catch (java.io.IOException exception) {
            showError("The catalog could not be loaded.", "Recover a book");
            return;
        }
        refreshActiveLoans(membersById);
        Member selected = (Member) member.getSelectedItem();
        if (mode == Mode.OVERDUE) {
            rows.addAll(lending.activeLoans().stream()
                    .filter(loan -> loan.dueDate().isBefore(LocalDate.now()))
                    .sorted(Comparator
                            .comparingLong((Loan loan) -> services.LoanRules.daysOverdue(loan, LocalDate.now()))
                            .reversed())
                    .toList());
        } else if (selected != null) {
            List<Loan> memberLoans = lending.loansForMember(selected.memberId());
            rows.addAll(memberLoans.stream().filter(loan -> mode == Mode.LOST
                    ? loan.status() == LoanStatus.ACTIVE
                    : loan.status() == LoanStatus.ACTIVE
                            || (includeLost.isSelected() && loan.status() == LoanStatus.LOST))
                    .toList());
        }
        for (Loan loan : rows) {
            long overdue = services.LoanRules.daysOverdue(loan, LocalDate.now());
            Book book = booksById.get(loan.bookId());
            BigDecimal fine = book == null ? BigDecimal.ZERO.setScale(2)
                    : loan.status() == LoanStatus.LOST
                            ? loan.overdueFine()
                            : ChargeCalculator.returnCharges(loan, book, LocalDate.now(), ReturnCondition.GOOD,
                                    BigDecimal.ZERO, BigDecimal.ZERO).stream()
                                    .filter(line -> line.type() == ChargeType.OVERDUE_FINE)
                                    .map(ChargeCalculator.Line::amount).findFirst().orElse(BigDecimal.ZERO.setScale(2));
            Member loanMember = membersById.get(loan.memberId());
            String memberLabel = loanMember == null ? loan.memberId()
                    : loanMember.name() + "  |  " + loanMember.phone();
            tableModel.addRow(new Object[] { false, memberLabel, loan.bookTitleSnapshot(), loan.dueDate(), overdue,
                    ReturnCondition.GOOD, "0.00", money(fine) });
        }
        boolean overdueView = mode == Mode.OVERDUE;
        summary.setText(overdueView ? rows.size() + " overdue active loan(s), sorted longest overdue first."
                : rows.isEmpty() ? "No loans to show." : rows.size() + " loan(s) available for this action.");
        includeLost.setVisible(mode == Mode.RETURNS);
        collectNow.setVisible(mode == Mode.RETURNS);
        if (overdueView) {
            for (int column = 0; column < table.getColumnCount(); column++)
                table.getColumnModel().getColumn(column).setPreferredWidth(column == 1 ? 220 : 110);
        }
        refreshBill();
    }

    public void setLoanSearch(String query) {
        loanSearch = query == null ? "" : query;
        try {
            Map<String, Member> membersById = new HashMap<>();
            members.listSorted(actor).forEach(item -> membersById.put(item.memberId(), item));
            refreshActiveLoans(membersById);
        } catch (java.io.IOException exception) {
            showError("Members could not be loaded. Try refreshing the recovery pane.", "Recover a book");
        }
    }

    private void refreshActiveLoans(Map<String, Member> membersById) {
        String needle = loanSearch.trim().toLowerCase(java.util.Locale.ROOT);
        activeLoansModel.setRowCount(0);
        for (Loan loan : lending.activeLoans()) {
            Member loanMember = membersById.get(loan.memberId());
            String memberName = loanMember == null ? loan.memberId() : loanMember.name();
            String memberSearch = memberName + " " + loan.memberId()
                    + (loanMember == null ? "" : " " + loanMember.phone() + " " + loanMember.email());
            String bookSearch = loan.bookTitleSnapshot() + " " + loan.bookId();
            if (!needle.isBlank() && !(memberSearch + " " + bookSearch).toLowerCase(java.util.Locale.ROOT)
                    .contains(needle)) continue;
            activeLoansModel.addRow(new Object[] { loan.bookTitleSnapshot(), memberName, loan.memberId(),
                    loan.dueDate(), loan.status() });
        }
    }

    private void refreshBill() {
        StringBuilder details = new StringBuilder();
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (int row = 0; row < rows.size(); row++) {
            if (row >= tableModel.getRowCount())
                break;
            if (!Boolean.TRUE.equals(tableModel.getValueAt(row, 0)))
                continue;
            Loan loan = rows.get(row);
            Book book = booksById.get(loan.bookId());
            if (book == null)
                continue;
            ReturnCondition condition = (ReturnCondition) tableModel.getValueAt(row, 5);
            BigDecimal damage;
            try {
                damage = new BigDecimal(tableModel.getValueAt(row, 6).toString());
            } catch (NumberFormatException exception) {
                details.append(loan.bookTitleSnapshot()).append(": invalid damage amount\n");
                continue;
            }
            List<ChargeCalculator.Line> lines = loan.status() == LoanStatus.LOST
                    ? ChargeCalculator.lostCharges(loan, book, LocalDate.now(), settings.getMoney("fee.lostProcessing"))
                    : returns.preview(loan, book, LocalDate.now(), condition, damage);
            BigDecimal subtotal = lines.stream().map(ChargeCalculator.Line::amount)
                    .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            details.append(loan.bookTitleSnapshot()).append(":\n");
            lines.forEach(line -> details.append("  ").append(line.description()).append("  Rs. ")
                    .append(line.amount().toPlainString()).append('\n'));
            details.append("  Subtotal  Rs. ").append(subtotal.toPlainString()).append('\n');
            grandTotal = grandTotal.add(subtotal);
        }
        summary.setText(details.isEmpty()
                ? (rows.isEmpty() ? "No loans to show." : "Select loans to preview the itemized bill.")
                : details + "Grand total: Rs. " + grandTotal.setScale(2).toPlainString());
    }

    private void receiveSelected() {
        List<ReturnService.ReturnRequest> requests = new ArrayList<>();
        for (int row = 0; row < rows.size(); row++) {
            if (!Boolean.TRUE.equals(tableModel.getValueAt(row, 0)))
                continue;
            if (row >= tableModel.getRowCount())
                continue;
            ReturnCondition condition = (ReturnCondition) tableModel.getValueAt(row, 5);
            BigDecimal damage;
            try {
                damage = new BigDecimal(tableModel.getValueAt(row, 6).toString());
            } catch (NumberFormatException exception) {
                showError("Enter a valid damage amount.", "Return");
                return;
            }
            requests.add(new ReturnService.ReturnRequest(rows.get(row).loanId(), condition, damage));
        }
        if (requests.isEmpty()) {
            showError("Select one or more loans to return.", "Return");
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Record the selected returns?", "Confirm returns",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION)
            return;
        try {
            List<Loan> returned = returns.receiveMany(actor, requests);
            if (collectNow.isSelected())
                collectReturnedCharges(returned);
            refreshRows();
            showSuccess("Return recorded and charges added to the member ledger.");
        } catch (Exception exception) {
            showError(exception.getMessage(), "Return error");
        }
    }

    private void collectReturnedCharges(List<Loan> returned) throws java.io.IOException {
        Map<String, List<Loan>> byMember = returned.stream().collect(
                java.util.stream.Collectors.groupingBy(Loan::memberId, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        for (Map.Entry<String, List<Loan>> entry : byMember.entrySet()) {
            String memberId = entry.getKey();
            List<String> chargeIds = charges.forMember(memberId).stream()
                    .filter(charge -> entry.getValue().stream().anyMatch(loan -> loan.loanId().equals(charge.loanId())))
                    .filter(charge -> charge.type() != ChargeType.REFUND && charge.balance().signum() > 0)
                    .map(Charge::chargeId).toList();
            if (chargeIds.isEmpty())
                continue;
            BigDecimal total = charges.forMember(memberId).stream()
                    .filter(charge -> chargeIds.contains(charge.chargeId())).map(Charge::balance)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            PaymentMethod method = (PaymentMethod) JOptionPane.showInputDialog(this,
                    "Payment method for member " + memberId, "Collect return charges",
                    JOptionPane.PLAIN_MESSAGE, null, PaymentMethod.values(), PaymentMethod.CASH);
            if (method == null)
                continue;
            payments.pay(actor, memberId, chargeIds, total, method);
        }
    }

    public void markSelectedLost() {
        int row = table.getSelectedRow();
        if (row < 0) {
            for (int index = 0; index < rows.size(); index++) {
                if (Boolean.TRUE.equals(tableModel.getValueAt(index, 0))) {
                    row = index;
                    break;
                }
            }
        }
        if (row < 0 || row >= rows.size() || rows.get(row).status() != LoanStatus.ACTIVE) {
            return;
        }
        Loan selected = rows.get(row);
        try {
            Book book = catalog.loadBooks().stream().filter(item -> item.bookId().equals(selected.bookId()))
                    .findFirst().orElseThrow();
            String bill = ChargeCalculator.lostCharges(selected, book, LocalDate.now(),
                    settings.getMoney("fee.lostProcessing")).stream()
                    .map(line -> line.description() + "  Rs. " + line.amount().toPlainString())
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (JOptionPane.showConfirmDialog(this, bill, "Confirm lost book", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION)
                return;
            lost.markLost(actor, selected.loanId());
            refreshRows();
        } catch (Exception exception) {
            showError(exception.getMessage(), "Lost-book error");
        }
    }

    public void replaceMemberCard() {
        Member selected = (Member) member.getSelectedItem();
        if (selected == null)
            return;
        BigDecimal fee = settings.getMoney("fee.cardReplacement");
        if (JOptionPane.showConfirmDialog(this, "Add card replacement charge of Rs. " + fee.toPlainString()
                + " for " + selected.name() + "?", "Replace member card", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION)
            return;
        try {
            if (fee.signum() > 0)
                charges.add(selected.memberId(), "", ChargeType.CARD_REPLACEMENT,
                        "Member card replacement", fee);
            showSuccess(fee.signum() > 0 ? "Card replacement charge added to dues." : "Card replacement recorded.");
        } catch (Exception exception) {
            showError(exception.getMessage(), "Card replacement");
        }
    }

    private void showSuccess(String message) {
        JOptionPane.showMessageDialog(this, message, "Recover a book", JOptionPane.INFORMATION_MESSAGE);
    }

    private void showError(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private static String money(BigDecimal amount) {
        return "Rs. " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
