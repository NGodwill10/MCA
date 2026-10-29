package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.RowFilter;
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

    private final transient ArrayList<Book> books = new ArrayList<>();
    private final JTable table;
    private final transient TableRowSorter<BookTableModel> sorter;
    private final transient BookTableModel tableModel;
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel content = new JPanel(contentLayout);
    private final JLabel count = new JLabel();
    private final Timer searchTimer;
    private String searchQuery = "";
    private String searchField = "All";
    private boolean availableOnly;
    private transient Runnable selectionChanged = () -> {
    };

    public BookCatalogPanel(List<Book> books) {
        this.books.addAll(sorted(books));
        tableModel = new BookTableModel(this.books);
        this.table = new JTable(tableModel);
        this.sorter = new TableRowSorter<>(tableModel);
        this.searchTimer = new Timer(250, event -> applySearch());
        buildPanel();
    }

    public void setSearchText(String text) {
        searchQuery = text;
        searchTimer.restart();
    }

    public void setFilters(String field, String query, boolean showAvailableOnly) {
        searchField = field;
        searchQuery = query;
        availableOnly = showAvailableOnly;
        searchTimer.restart();
    }

    public void setSelectionChanged(Runnable listener) {
        selectionChanged = listener;
    }

    public Optional<Book> selectedBook() {
        int selectedRow = table.getSelectedRow();
        if (selectedRow < 0) {
            return Optional.empty();
        }
        int modelRow = table.convertRowIndexToModel(selectedRow);
        return Optional.of(tableModel.bookAt(modelRow));
    }

    public void setBooks(List<Book> updatedBooks) {
        String selectedBookId = selectedBook().map(Book::bookId).orElse(null);
        books.clear();
        books.addAll(sorted(updatedBooks));
        tableModel.fireTableDataChanged();
        if (selectedBookId == null) {
            table.clearSelection();
        } else {
            int selectedModelRow = -1;
            for (int row = 0; row < books.size(); row++) {
                if (selectedBookId.equals(books.get(row).bookId())) {
                    selectedModelRow = row;
                    break;
                }
            }
            if (selectedModelRow >= 0) {
                int selectedViewRow = table.convertRowIndexToView(selectedModelRow);
                table.setRowSelectionInterval(selectedViewRow, selectedViewRow);
            } else {
                table.clearSelection();
            }
        }
        contentLayout.show(content, books.isEmpty() ? "empty" : "table");
        updateCount();
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
            }
        });

        JPanel empty = new JPanel(new BorderLayout());
        empty.setBackground(Theme.WHITE);
        JLabel emptyMessage = new JLabel("No books yet. Use Add book on the left.", SwingConstants.CENTER);
        emptyMessage.setForeground(Theme.MUTED);
        emptyMessage.setFont(Theme.BODY);
        empty.add(emptyMessage, BorderLayout.CENTER);
        content.add(new JScrollPane(table), "table");
        content.add(empty, "empty");
        contentLayout.show(content, books.isEmpty() ? "empty" : "table");
        add(content, BorderLayout.CENTER);
        count.setForeground(Theme.MUTED);
        count.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        add(count, BorderLayout.SOUTH);
        updateCount();
    }

    private void applySearch() {
        String query = searchQuery.trim().toLowerCase(Locale.ROOT);
        sorter.setRowFilter(new RowFilter<>() {
            @Override
            public boolean include(Entry<? extends BookTableModel, ? extends Integer> entry) {
                Book book = books.get(entry.getIdentifier());
                if (availableOnly && book.availableCopies() < 1) {
                    return false;
                }
                if (query.isEmpty()) {
                    return true;
                }
                if (searchField.equals("All")) {
                    return searchableValues(book).stream().anyMatch(value -> contains(value, query));
                }
                return contains(searchValue(book, searchField), query);
            }
        });
        updateCount();
    }

    private void updateCount() {
        count.setText("Showing " + table.getRowCount() + " of " + books.size() + " books");
    }

    private static List<Book> sorted(List<Book> books) {
        return books.stream()
                .sorted((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.title(), right.title())).toList();
    }

    private static boolean contains(String value, String query) {
        return value.toLowerCase(Locale.ROOT).contains(query);
    }

    private static List<String> searchableValues(Book book) {
        return List.of(book.title(), book.authors(), book.isbn(), book.publisher(), book.category(),
                book.language(), book.publicationYear());
    }

    private static String searchValue(Book book, String field) {
        return switch (field) {
            case "Title" -> book.title();
            case "Author" -> book.authors();
            case "ISBN" -> book.isbn();
            case "Publisher" -> book.publisher();
            case "Category" -> book.category();
            case "Language" -> book.language();
            case "Year" -> book.publicationYear();
            default -> "";
        };
    }

    private static final class BookTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {
                "Title", "Author(s)", "ISBN", "Publisher", "Category", "Language", "Year", "Available / Total" };
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
                case 4 -> book.category();
                case 5 -> book.language();
                case 6 -> book.publicationYear();
                case 7 -> book.availableCopies() + " / " + book.totalCopies();
                default -> "";
            };
        }

        private Book bookAt(int row) {
            return books.get(row);
        }
    }
}
