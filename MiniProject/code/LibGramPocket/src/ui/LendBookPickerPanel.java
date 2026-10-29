package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import model.Book;

public final class LendBookPickerPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient List<Book> books = new ArrayList<>();
    private final BookTableModel tableModel = new BookTableModel(books);
    private final JTable table = new JTable(tableModel) {
        private static final long serialVersionUID = 1L;

        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            if (row >= 0) {
                Book book = books.get(convertRowIndexToModel(row));
                if (book.ownershipType() == model.Book.OwnershipType.BORROWED) {
                    return "Borrowed from " + lenderName.apply(book.lenderContactId()) + ". Can't be lent out";
                }
            }
            return super.getToolTipText(event);
        }
    };
    private final JLabel count = new JLabel("0 books", SwingConstants.TRAILING);
    private final JLabel status = new JLabel("Select a row to add an available owned book.");
    private final JButton add = new JButton("Add selected book");
    private transient Consumer<Book> addHandler = book -> {
    };
    private transient Function<String, String> lenderName = id -> id;

    public LendBookPickerPanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createTitledBorder("Choose books"));
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setRowHeight(26);
        table.setPreferredScrollableViewportSize(new Dimension(520, 280));
        table.getAccessibleContext().setAccessibleName("Books available to lend");
        table.getSelectionModel().addListSelectionListener(event -> updateAddState());
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    addSelected();
                }
            }
        });
        table.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                    boolean focused, int row, int column) {
                java.awt.Component component = super.getTableCellRendererComponent(
                        source, value, selected, focused, row, column);
                if (!selected && books.get(source.convertRowIndexToModel(row)).availableCopies() == 0) {
                    component.setForeground(Theme.MUTED);
                }
                Book book = books.get(source.convertRowIndexToModel(row));
                if (book.ownershipType() == model.Book.OwnershipType.BORROWED) {
                    component.setForeground(selected ? source.getSelectionForeground() : Theme.MUTED);
                    ((javax.swing.JComponent) component).setToolTipText(
                            "Borrowed from " + lenderName.apply(book.lenderContactId()) + ". Can't be lent out");
                } else {
                    ((javax.swing.JComponent) component).setToolTipText(null);
                }
                return component;
            }
        });
        add.addActionListener(event -> addSelected());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        actions.setOpaque(false);
        actions.add(add);
        actions.add(status);
        add(new JScrollPane(table), BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
        add(count, BorderLayout.NORTH);
    }

    public void setBooks(List<Book> newBooks) {
        String selectedBookId = selectedBookId();
        books.clear();
        books.addAll(newBooks);
        tableModel.fireTableDataChanged();
        restoreSelection(selectedBookId);
        updateAddState();
        count.setText(books.size() + (books.size() == 1 ? " book" : " books"));
        status.setText(books.isEmpty() ? "No books match this search."
                : "Select a row to add an available owned book.");
    }

    private String selectedBookId() {
        int selected = table.getSelectedRow();
        return selected < 0 ? null : books.get(table.convertRowIndexToModel(selected)).bookId();
    }

    private void restoreSelection(String bookId) {
        if (bookId == null) {
            table.clearSelection();
            return;
        }
        for (int modelRow = 0; modelRow < books.size(); modelRow++) {
            if (bookId.equals(books.get(modelRow).bookId())) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    return;
                }
            }
        }
        table.clearSelection();
    }

    public void setAddHandler(Consumer<Book> handler) {
        addHandler = handler == null ? book -> {
        } : handler;
    }

    public void setLenderNameResolver(Function<String, String> resolver) {
        lenderName = resolver == null ? id -> id : resolver;
    }

    public void showStatus(String message) {
        status.setText(message);
    }

    private void updateAddState() {
        int selected = table.getSelectedRow();
        Book book = selected < 0 ? null : books.get(table.convertRowIndexToModel(selected));
        boolean allowed = book != null && book.ownershipType() == model.Book.OwnershipType.OWNED
                && book.availableCopies() > 0;
        add.setEnabled(allowed);
        add.setToolTipText(book == null ? "Select an available owned book"
                : book.ownershipType() == model.Book.OwnershipType.BORROWED
                        ? "Borrowed from " + lenderName.apply(book.lenderContactId()) + ". Can't be lent out"
                        : book.availableCopies() < 1 ? "No copies available" : "Add selected book to the lend list");
    }

    private void addSelected() {
        int selected = table.getSelectedRow();
        if (selected < 0) {
            showStatus("Select a book first.");
            return;
        }
        Book book = books.get(table.convertRowIndexToModel(selected));
        if (book.ownershipType() == model.Book.OwnershipType.BORROWED) {
            showStatus("Borrowed from " + lenderName.apply(book.lenderContactId()) + ". Can't be lent out.");
            return;
        }
        if (book.availableCopies() < 1) {
            showStatus("No copies of \"" + book.title() + "\" are available.");
            return;
        }
        addHandler.accept(book);
    }

    private static final class BookTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = { "Title", "Author(s)", "Available", "Ownership" };
        private final transient List<Book> books;

        private BookTableModel(List<Book> books) {
            this.books = books;
        }

        @Override
        public int getRowCount() {
            return books.size();
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
        public Object getValueAt(int row, int column) {
            Book book = books.get(row);
            return switch (column) {
                case 0 -> book.title();
                case 1 -> book.authors();
                case 2 -> book.availableCopies() == 0 ? "No copies available" : book.availableCopies();
                case 3 -> book.ownershipType() == model.Book.OwnershipType.BORROWED ? "Borrowed" : "Owned";
                default -> "";
            };
        }
    }
}
