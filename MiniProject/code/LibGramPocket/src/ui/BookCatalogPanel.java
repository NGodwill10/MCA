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
import javax.swing.Timer;
import javax.swing.event.RowSorterEvent;
import javax.swing.event.RowSorterListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import model.Book;

public final class BookCatalogPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient List<Book> books = new ArrayList<>();
    private final JTable table;
    private final transient TableRowSorter<BookTableModel> sorter;
    private final transient BookTableModel tableModel;
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel content = new JPanel(contentLayout);
    private final JLabel count = new JLabel();
    private final JLabel emptyMessage = new JLabel("Your catalog is empty. Use Add book on the left.",
            SwingConstants.CENTER);
    private transient Runnable selectionChanged = () -> {
    };
    private transient Runnable sortChanged = () -> {
    };

    public BookCatalogPanel(List<Book> books) {
        this.books.addAll(books);
        tableModel = new BookTableModel(this.books);
        table = new JTable(tableModel);
        sorter = new TableRowSorter<>(tableModel);
        buildPanel();
    }

    public Optional<Book> selectedBook() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return Optional.empty();
        }
        return Optional.of(tableModel.bookAt(table.convertRowIndexToModel(selectedRow)));
    }

    public boolean selectBook(String bookId) {
        for (int modelRow = 0; modelRow < books.size(); modelRow++) {
            if (books.get(modelRow).bookId().equals(bookId)) {
                int viewRow = table.convertRowIndexToView(modelRow);
                if (viewRow >= 0) {
                    table.setRowSelectionInterval(viewRow, viewRow);
                    table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
                    return true;
                }
            }
        }
        return false;
    }

    public void setSelectionChanged(Runnable listener) {
        selectionChanged = listener;
    }

    public void setSortChanged(Runnable listener) {
        sortChanged = listener == null ? () -> {
        } : listener;
    }

    public int sortColumn() {
        return sorter.getSortKeys().isEmpty() ? 0 : sorter.getSortKeys().getFirst().getColumn();
    }

    public SortOrder sortOrder() {
        return sorter.getSortKeys().isEmpty() ? SortOrder.ASCENDING : sorter.getSortKeys().getFirst().getSortOrder();
    }

    public void setSort(int column, SortOrder order) {
        if (column < 0 || column >= tableModel.getColumnCount()) {
            throw new IllegalArgumentException("Invalid catalog sort column.");
        }
        sorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(column, order)));
    }

    public void setBooks(List<Book> visibleBooks, int totalBooks) {
        String selectedBookId = selectedBook().map(Book::bookId).orElse(null);
        books.clear();
        books.addAll(visibleBooks);
        tableModel.fireTableDataChanged();
        restoreSelection(selectedBookId);
        emptyMessage.setText(totalBooks == 0
                ? "Your catalog is empty. Use Add book on the left."
                : "No books match these search filters.");
        contentLayout.show(content, books.isEmpty() ? "empty" : "table");
        count.setText(books.size() + " of " + totalBooks + " books");
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

    private void updateCount() {
        count.setText(table.getRowCount() + " of " + books.size() + " books");
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
        sorter.addRowSorterListener(new RowSorterListener() {
            @Override
            public void sorterChanged(RowSorterEvent event) {
                updateCount();
                if (event.getType() == RowSorterEvent.Type.SORT_ORDER_CHANGED) {
                    sortChanged.run();
                }
            }
        });

        JPanel empty = new JPanel(new BorderLayout());
        empty.setBackground(Theme.WHITE);
        emptyMessage.setForeground(Theme.MUTED);
        empty.add(emptyMessage, BorderLayout.CENTER);
        content.add(new JScrollPane(table), "table");
        content.add(empty, "empty");
        contentLayout.show(content, books.isEmpty() ? "empty" : "table");
        add(content, BorderLayout.CENTER);
        count.setForeground(Theme.MUTED);
        count.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        add(count, BorderLayout.SOUTH);
        count.setText(books.size() + " of " + books.size() + " books");
    }

    private static final class BookTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {
                "Title", "Author(s)", "ISBN", "Publisher", "Year", "Category", "Reading status", "Condition",
                "Available / Owned", "Ownership" };
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
        public Object getValueAt(int rowIndex, int columnIndex) {
            Book book = books.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> book.title();
                case 1 -> book.authors();
                case 2 -> book.isbn();
                case 3 -> book.publisher();
                case 4 -> book.publicationYear();
                case 5 -> book.category();
                case 6 -> titleCase(book.readingStatus().name());
                case 7 -> titleCase(book.condition().name());
                case 8 -> book.availableCopies() + " / " + book.totalCopies();
                case 9 -> book.ownershipType() == model.Book.OwnershipType.BORROWED ? "Borrowed" : "Owned";
                default -> "";
            };
        }

        private Book bookAt(int row) {
            return books.get(row);
        }

        private static String titleCase(String value) {
            return value.charAt(0) + value.substring(1).toLowerCase(java.util.Locale.ROOT);
        }
    }
}
