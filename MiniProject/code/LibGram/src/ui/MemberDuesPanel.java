package ui;

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

public final class MemberDuesPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String EMPTY = "No dues recorded.";
    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel empty = new JLabel(EMPTY, SwingConstants.CENTER);
    private final JLabel summary = new JLabel("Outstanding: Rs. 0.00");

    public MemberDuesPanel() {
        super(new BorderLayout());
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createTitledBorder("Dues"));
        empty.setForeground(Theme.MUTED);
        content.setBackground(Theme.WHITE);
        add(content, BorderLayout.CENTER);
        setData(List.of(), List.of(), "Rs. 0.00", "Rs. 0.00");
    }

    public void setData(List<List<String>> rows) {
        setData(rows, List.of(), "Rs. 0.00", "Rs. 0.00");
    }

    public void setData(List<List<String>> rows, List<List<String>> payments, String outstanding,
            String refundCredit) {
        content.removeAll();
        summary.setText("Outstanding: " + outstanding
                + (refundCredit.equals("Rs. 0.00") ? "" : "    Refund due to member: " + refundCredit));
        add(summary, BorderLayout.NORTH);
        if (rows.isEmpty()) {
            content.add(empty, BorderLayout.CENTER);
        } else {
            DefaultTableModel model = new DefaultTableModel(
                    new String[] { "Date", "Description", "Type", "Amount", "Waived", "Paid", "Balance", "Status" },
                    0) {
                private static final long serialVersionUID = 1L;

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
            rows.forEach(row -> model.addRow(row.toArray()));
            JPanel tables = new JPanel(new BorderLayout(0, 6));
            tables.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
            if (!payments.isEmpty()) {
                DefaultTableModel paymentModel = new DefaultTableModel(
                        new String[] { "Paid at", "Amount", "Method", "Received by", "Receipt" }, 0) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };
                payments.forEach(row -> paymentModel.addRow(row.toArray()));
                tables.add(new JScrollPane(new JTable(paymentModel)), BorderLayout.SOUTH);
            }
            content.add(tables, BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }
}
