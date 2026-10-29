package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import model.Contact;

public final class ContactCatalogPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient List<Contact> contacts = new ArrayList<>();
    private final ContactTableModel tableModel = new ContactTableModel(contacts);
    private final JTable table = new JTable(tableModel);
    private final transient TableRowSorter<ContactTableModel> sorter = new TableRowSorter<>(tableModel);
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel content = new JPanel(contentLayout);
    private final JLabel count = new JLabel();
    private final JLabel emptyMessage = new JLabel("No contacts yet. Use Add contact on the left.",
            SwingConstants.CENTER);
    private transient Runnable selectionChanged = () -> {
    };

    public ContactCatalogPanel(List<Contact> contacts) {
        buildPanel();
        setContacts(contacts, contacts.size());
    }

    public Optional<Contact> selectedContact() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return Optional.empty();
        }
        return Optional.of(tableModel.contactAt(table.convertRowIndexToModel(selectedRow)));
    }

    public void setSelectionChanged(Runnable listener) {
        selectionChanged = listener;
    }

    public void setContacts(List<Contact> visibleContacts, int totalContacts) {
        String selectedContactId = selectedContact().map(Contact::contactId).orElse(null);
        contacts.clear();
        contacts.addAll(visibleContacts);
        tableModel.fireTableDataChanged();
        restoreSelection(selectedContactId);
        emptyMessage.setText(totalContacts == 0
                ? "No contacts yet. Use Add contact on the left."
                : "No contacts match this search.");
        count.setText(contacts.size() + " of " + totalContacts + " contacts");
        updateEmptyState();
        selectionChanged.run();
    }

    private void restoreSelection(String contactId) {
        if (contactId == null) {
            table.clearSelection();
            return;
        }
        for (int modelRow = 0; modelRow < contacts.size(); modelRow++) {
            if (contactId.equals(contacts.get(modelRow).contactId())) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    return;
                }
            }
        }
        table.clearSelection();
    }

    private void buildPanel() {
        setLayout(new BorderLayout(0, Theme.GAP));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createLineBorder(Theme.LINE));
        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setFont(Theme.BODY);
        table.setShowVerticalLines(false);
        sorter.setComparator(0, String.CASE_INSENSITIVE_ORDER);
        sorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(0, SortOrder.ASCENDING)));
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectionChanged.run();
            }
        });

        JPanel empty = new JPanel(new BorderLayout());
        empty.setBackground(Theme.WHITE);
        emptyMessage.setForeground(Theme.MUTED);
        empty.add(emptyMessage, BorderLayout.CENTER);
        content.setBackground(Theme.WHITE);
        content.add(new JScrollPane(table), "table");
        content.add(empty, "empty");
        add(content, BorderLayout.CENTER);
        count.setForeground(Theme.MUTED);
        count.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        add(count, BorderLayout.SOUTH);
        updateEmptyState();
    }

    private void updateEmptyState() {
        boolean empty = contacts.isEmpty();
        contentLayout.show(content, empty ? "empty" : "table");
        revalidate();
        repaint();
    }

    private static final class ContactTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = { "Name", "Phone", "Email", "Notes" };
        private final transient List<Contact> contacts;

        private ContactTableModel(List<Contact> contacts) {
            this.contacts = contacts;
        }

        @Override
        public int getRowCount() {
            return contacts.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Contact contact = contacts.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> contact.name();
                case 1 -> contact.phone();
                case 2 -> contact.email();
                case 3 -> shorten(contact.notes());
                default -> "";
            };
        }

        private Contact contactAt(int row) {
            return contacts.get(row);
        }

        private static String shorten(String value) {
            // ASSUMPTION: The list preview is limited to 48 characters.
            String singleLine = value.replace('\n', ' ').replace('\r', ' ');
            return singleLine.length() <= 48 ? singleLine : singleLine.substring(0, 45) + "...";
        }
    }
}
