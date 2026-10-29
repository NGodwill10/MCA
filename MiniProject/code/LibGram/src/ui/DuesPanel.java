package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import model.Charge;
import model.ChargeType;
import model.Member;
import model.Payment;
import model.PaymentMethod;
import model.User;
import services.ChargeService;
import services.MemberService;
import services.PaymentService;
import services.SettingsService;

public final class DuesPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient User actor;
    private final transient MemberService members;
    private final transient ChargeService charges;
    private final transient PaymentService payments;
    private final transient SettingsService settings;
    private final JComboBox<Member> member = new JComboBox<>();
    private final JTable table = new JTable();
    private final JTable paymentsTable = new JTable();
    private final JTable pendingMembersTable = new JTable();
    private final JPanel views = new JPanel(new CardLayout());
    private final JLabel viewLabel = new JLabel("Outstanding charges");
    private final JLabel totals = new JLabel();
    private final JButton paySelected = new JButton("Pay selected");
    private final JButton payAll = new JButton("Pay all");
    private final JButton waive = new JButton("Waive");
    private final JButton addManual = new JButton("Add manual charge");
    private final JButton recordRefund = new JButton("Record refund");
    private transient List<Charge> visible = List.of();
    private transient List<Member> pendingMembers = List.of();
    private String pendingSearch = "";

    public DuesPanel(User actor, MemberService members, ChargeService charges, PaymentService payments,
            SettingsService settings) throws java.io.IOException {
        this.actor = actor;
        this.members = members;
        this.charges = charges;
        this.payments = payments;
        this.settings = settings;
        build();
    }

    private void build() throws java.io.IOException {
        setLayout(new BorderLayout(8, 8));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.setOpaque(false);
        top.add(new JLabel("Member"));
        member.setRenderer(new javax.swing.DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean selected, boolean focus) {
                return super.getListCellRendererComponent(list,
                        value instanceof Member selectedMember ? selectedMember.memberId() + "  |  "
                                + selectedMember.name() : "Choose a member",
                        index, selected, focus);
            }
        });
        for (Member item : members.listSorted(actor))
            member.addItem(item);
        member.addActionListener(event -> refresh());
        top.add(member);
        paySelected.addActionListener(event -> collect(false));
        payAll.addActionListener(event -> collect(true));
        waive.addActionListener(event -> waiveSelected());
        addManual.addActionListener(event -> addManualCharge());
        recordRefund.addActionListener(event -> refundSelected());
        boolean admin = actor.role() == User.Role.ADMIN;
        waive.setEnabled(admin);
        addManual.setEnabled(admin);
        recordRefund.setEnabled(admin);
        top.add(paySelected);
        top.add(payAll);
        top.add(waive);
        top.add(addManual);
        top.add(recordRefund);
        add(top, BorderLayout.NORTH);
        table.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setFillsViewportHeight(true);
        viewLabel.setFont(Theme.HEADING);
        viewLabel.setBorder(BorderFactory.createEmptyBorder(0, 2, 4, 0));
        views.setOpaque(false);
        views.add(new JScrollPane(table), "CHARGES");
        views.add(new JScrollPane(paymentsTable), "PAYMENTS");
        JPanel details = new JPanel(new BorderLayout());
        details.setOpaque(false);
        details.add(viewLabel, BorderLayout.NORTH);
        details.add(views, BorderLayout.CENTER);
        pendingMembersTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        pendingMembersTable.setFillsViewportHeight(true);
        pendingMembersTable.getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            int row = pendingMembersTable.getSelectedRow();
            if (row < 0 || row >= pendingMembers.size()) return;
            Member selected = pendingMembers.get(pendingMembersTable.convertRowIndexToModel(row));
            for (int index = 0; index < member.getItemCount(); index++) {
                if (member.getItemAt(index).memberId().equals(selected.memberId())) {
                    member.setSelectedIndex(index);
                    break;
                }
            }
        });
        JPanel pendingPanel = new JPanel(new BorderLayout(0, 4));
        pendingPanel.setOpaque(false);
        pendingPanel.add(new JLabel("Members with pending dues"), BorderLayout.NORTH);
        pendingPanel.add(new JScrollPane(pendingMembersTable), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, pendingPanel, details);
        split.setResizeWeight(0.34);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        totals.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));
        add(totals, BorderLayout.SOUTH);
        refreshPendingMembers("");
        refresh();
    }

    public void refreshMembers() {
        String selectedId = member.getSelectedItem() instanceof Member selected ? selected.memberId() : null;
        member.removeAllItems();
        try {
            for (Member item : members.listSorted(actor)) member.addItem(item);
            if (selectedId != null) {
                for (int index = 0; index < member.getItemCount(); index++) {
                    if (member.getItemAt(index).memberId().equals(selectedId)) {
                        member.setSelectedIndex(index);
                        break;
                    }
                }
            }
            refresh();
        } catch (java.io.IOException exception) {
            showError("Members could not be loaded.", "Dues");
        }
    }

    public void refresh() {
        refreshPendingMembers(pendingSearch);
        Member selectedMember = (Member) member.getSelectedItem();
        String memberId = selectedMember == null ? "" : selectedMember.memberId();
        visible = charges.forMember(memberId);
        DefaultTableModel model = new DefaultTableModel(
                new String[] { "Date", "Description", "Type", "Amount", "Waived", "Paid", "Balance", "Status" }, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (Charge charge : visible) {
            model.addRow(new Object[] { charge.createdAt().toLocalDate(), charge.description(), charge.type(),
                    money(charge.amount()), money(charge.waivedAmount()), money(charge.paidAmount()),
                    money(charge.balance().abs()), status(charge.status()) });
        }
        table.setModel(model);
        DefaultTableModel paymentModel = new DefaultTableModel(
                new String[] { "Date", "Amount", "Method", "Received by", "Payment ID" }, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        for (Payment payment : payments.history(memberId)) {
            paymentModel.addRow(new Object[] { payment.paidAt(), money(payment.amount().abs()),
                    payment.method(), payment.receivedBy(), payment.paymentId() });
        }
        paymentsTable.setModel(paymentModel);
        paymentsTable.setFillsViewportHeight(true);
        BigDecimal refund = charges.refundDue(memberId);
        totals.setText("Outstanding: " + money(charges.unpaidBalance(memberId))
                + (refund.signum() > 0 ? "    Refund due to member: " + money(refund) : ""));
        recordRefund.setEnabled(actor.role() == User.Role.ADMIN && refund.signum() > 0);
    }

    public void showPaymentView(boolean history) {
        ((CardLayout) views.getLayout()).show(views, history ? "PAYMENTS" : "CHARGES");
        viewLabel.setText(history ? "Payment history" : "Outstanding charges");
    }

    public void setPendingSearch(String query) {
        pendingSearch = query == null ? "" : query;
        refreshPendingMembers(pendingSearch);
    }

    private void refreshPendingMembers(String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        try {
            pendingMembers = members.listSorted(actor).stream()
                    .filter(item -> charges.unpaidBalance(item.memberId()).signum() > 0)
                    .filter(item -> needle.isBlank() || (item.memberId() + " " + item.name() + " "
                            + item.phone() + " " + item.email()).toLowerCase(java.util.Locale.ROOT).contains(needle))
                    .toList();
            DefaultTableModel model = new DefaultTableModel(new String[] { "Member", "Member ID", "Pending" }, 0) {
                private static final long serialVersionUID = 1L;
                @Override public boolean isCellEditable(int row, int column) { return false; }
            };
            for (Member item : pendingMembers)
                model.addRow(new Object[] { item.name(), item.memberId(), money(charges.unpaidBalance(item.memberId())) });
            pendingMembersTable.setModel(model);
        } catch (java.io.IOException exception) {
            showError("Members could not be loaded. Try refreshing the tab.", "Fees & Payments");
        }
    }

    private void collect(boolean all) {
        Member selectedMember = (Member) member.getSelectedItem();
        if (selectedMember == null)
            return;
        List<Charge> selectedCharges = all ? visible.stream().filter(charge -> charge.type() != ChargeType.REFUND
                && charge.balance().signum() > 0).toList() : selectedRows();
        List<String> ids = selectedCharges.stream().filter(charge -> charge.balance().signum() > 0)
                .map(Charge::chargeId).toList();
        if (ids.isEmpty()) {
            showError("Select one or more charges with a balance.", "Payment");
            return;
        }
        BigDecimal maxAmount = selectedCharges.stream().filter(charge -> ids.contains(charge.chargeId()))
                .map(Charge::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        String raw = JOptionPane.showInputDialog(this, "Amount to collect", maxAmount.setScale(2).toPlainString());
        if (raw == null)
            return;
        PaymentMethod method = chooseMethod("Payment method");
        if (method == null)
            return;
        try {
            PaymentService.Receipt receipt = payments.pay(actor, selectedMember.memberId(), ids,
                    new BigDecimal(raw), method);
            showReceipt(selectedMember, receipt);
            refresh();
        } catch (Exception exception) {
            showError(exception.getMessage(), "Payment error");
        }
    }

    private void waiveSelected() {
        List<Charge> selected = selectedRows();
        if (selected.size() != 1 || selected.getFirst().balance().signum() <= 0) {
            showError("Select one charge with an outstanding balance to waive.", "Waiver");
            return;
        }
        Charge charge = selected.getFirst();
        String reason = JOptionPane.showInputDialog(this, "Reason for waiver (required)");
        if (reason == null)
            return;
        String raw = JOptionPane.showInputDialog(this, "Amount to waive", charge.balance().toPlainString());
        if (raw == null)
            return;
        try {
            charges.waive(actor, charge.chargeId(), new BigDecimal(raw), reason);
            refresh();
        } catch (Exception exception) {
            showError(exception.getMessage(), "Waiver error");
        }
    }

    private void addManualCharge() {
        Member selectedMember = (Member) member.getSelectedItem();
        if (selectedMember == null)
            return;
        ChargeType[] allowed = { ChargeType.MEMBERSHIP_REGISTRATION, ChargeType.MEMBERSHIP_RENEWAL,
                ChargeType.CARD_REPLACEMENT };
        ChargeType type = (ChargeType) JOptionPane.showInputDialog(this, "Charge type", "Manual charge",
                JOptionPane.PLAIN_MESSAGE, null, allowed, allowed[0]);
        if (type == null)
            return;
        String key = switch (type) {
            case MEMBERSHIP_REGISTRATION -> services.SettingsDefaults.key("fee.membership.registration.",
                    selectedMember.membershipType());
            case MEMBERSHIP_RENEWAL -> services.SettingsDefaults.key("fee.membership.annual.",
                    selectedMember.membershipType());
            case CARD_REPLACEMENT -> "fee.cardReplacement";
            default -> throw new IllegalArgumentException("Unsupported manual charge type.");
        };
        String raw = JOptionPane.showInputDialog(this, "Amount", settings.getMoney(key).toPlainString());
        if (raw == null)
            return;
        String note = JOptionPane.showInputDialog(this, "Description");
        if (note == null || note.isBlank())
            return;
        try {
            charges.addManual(actor, selectedMember.memberId(), type, note.trim(), new BigDecimal(raw));
            refresh();
        } catch (Exception exception) {
            showError(exception.getMessage(), "Manual charge error");
        }
    }

    private void refundSelected() {
        Member selectedMember = (Member) member.getSelectedItem();
        if (selectedMember == null)
            return;
        List<Charge> refunds = visible.stream().filter(charge -> charge.type() == ChargeType.REFUND
                && charge.balance().signum() < 0).toList();
        if (refunds.isEmpty())
            return;
        Charge credit = (Charge) JOptionPane.showInputDialog(this, "Select refund credit", "Record refund",
                JOptionPane.PLAIN_MESSAGE, null, refunds.toArray(), refunds.getFirst());
        if (credit == null)
            return;
        String raw = JOptionPane.showInputDialog(this, "Refund amount", credit.balance().negate().toPlainString());
        if (raw == null)
            return;
        PaymentMethod method = chooseMethod("Refund method");
        if (method == null)
            return;
        try {
            List<Payment> refundPayments = payments.recordRefund(actor, selectedMember.memberId(),
                    credit.chargeId(), new BigDecimal(raw), method);
            showReceipt(selectedMember, new PaymentService.Receipt(refundPayments.getFirst().paymentId(),
                    refundPayments));
            refresh();
        } catch (Exception exception) {
            showError(exception.getMessage(), "Refund error");
        }
    }

    private void showReceipt(Member selectedMember, PaymentService.Receipt receipt) {
        Map<String, String> descriptions = visible.stream()
                .collect(Collectors.toMap(Charge::chargeId, Charge::description, (left, right) -> left));
        StringBuilder text = new StringBuilder("Receipt ").append(receipt.receiptNumber()).append('\n')
                .append(LocalDateTime.now()).append("\nMember: ").append(selectedMember.name()).append('\n');
        for (Payment payment : receipt.payments()) {
            text.append(descriptions.getOrDefault(payment.chargeId(), "Refund"))
                    .append("  Rs. ").append(payment.amount().abs().setScale(2)).append("  ")
                    .append(payment.method()).append("  Received by ").append(payment.receivedBy()).append('\n');
        }
        JTextArea receiptText = new JTextArea(text.toString(), 12, 55);
        receiptText.setEditable(false);
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JScrollPane(receiptText), BorderLayout.CENTER);
        JButton copy = new JButton("Copy receipt text");
        copy.addActionListener(event -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(receiptText.getText()), null));
        panel.add(copy, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, panel, "Receipt", JOptionPane.INFORMATION_MESSAGE);
    }

    private PaymentMethod chooseMethod(String label) {
        return (PaymentMethod) JOptionPane.showInputDialog(this, label, "Payment",
                JOptionPane.PLAIN_MESSAGE, null, PaymentMethod.values(), PaymentMethod.CASH);
    }

    private List<Charge> selectedRows() {
        return java.util.Arrays.stream(table.getSelectedRows()).mapToObj(table::convertRowIndexToModel)
                .filter(index -> index >= 0 && index < visible.size()).map(visible::get).toList();
    }

    private static String money(BigDecimal amount) {
        return "Rs. " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String status(model.ChargeStatus status) {
        String lower = status.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void showError(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
