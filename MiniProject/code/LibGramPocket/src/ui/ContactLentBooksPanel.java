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

public final class ContactLentBooksPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String EMPTY = "Nothing lent to this person yet.";
    private final JPanel content = new JPanel(new BorderLayout());
    private final JLabel empty = new JLabel(EMPTY, SwingConstants.CENTER);

    public ContactLentBooksPanel() {
        super(new BorderLayout(0, 6));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createTitledBorder("Books lent to this person"));
        empty.setForeground(Theme.MUTED);
        content.setBackground(Theme.WHITE);
        add(content, BorderLayout.CENTER);
        setData(List.of());
    }

    public void setData(List<List<String>> rows) {
        content.removeAll();
        if (rows.isEmpty()) {
            content.add(empty, BorderLayout.CENTER);
        } else {
            DefaultTableModel model = tableModel(rows);
            content.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);
        }
        revalidate();
        repaint();
    }

    private static DefaultTableModel tableModel(List<List<String>> rows) {
        DefaultTableModel model = new DefaultTableModel(
                new String[] { "Title", "Status", "Issue date", "Due date", "Return date", "Overdue days",
                        "Payment" },
                0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        rows.forEach(row -> model.addRow(row.toArray()));
        return model;
    }
}