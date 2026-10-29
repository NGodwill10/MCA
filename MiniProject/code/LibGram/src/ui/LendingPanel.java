package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.TableModelEvent;
import model.Book;
import model.Member;
import model.User;
import services.CatalogService;
import services.LendingService;
import services.MemberService;
import services.ReservationService;

/** Three-zone lending workspace: member context, book selection and lending cart. */
public final class LendingPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient User actor;
    private final transient CatalogService catalog;
    private final transient MemberService members;
    private final transient LendingService lending;
    private final transient ReservationService reservations;
    private final JTextField memberSearch = new JTextField(14);
    private final JTextField bookSearch = new JTextField(14);
    private final JComboBox<String> bookFilter = new JComboBox<>(new String[] { "All books", "Available only" });
    private final DefaultListModel<Member> memberModel = new DefaultListModel<>();
    private final JList<Member> memberList = new JList<>(memberModel);
    private final JLabel memberCard = new JLabel("<html><b>Select a member</b><br>Members available for lending will appear below.</html>");
    private final DefaultTableModel bookModel = new DefaultTableModel(
            new String[] { "Title", "Author(s)", "Category", "ISBN", "Available / Total", "Shelf" }, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable bookTable = new JTable(bookModel);
    private final JLabel bookCard = new JLabel("<html><b>Select a book</b><br>Book details will appear here.</html>");
    private final JButton addBook = new JButton("Add to lending list");
    private final JButton reserveBook = new JButton("Reserve selected");
    private final DefaultTableModel cartModel = new DefaultTableModel(new String[] { "Book", "Due date" }, 0) {
        private static final long serialVersionUID = 1L;
        @Override public boolean isCellEditable(int row, int column) { return column == 1; }
    };
    private final JTable cartTable = new JTable(cartModel);
    private final JLabel cartSummary = new JLabel("No books selected.");
    private final JLabel status = new JLabel(" ");
    private final JButton confirm = new JButton("Confirm lending");
    private final JButton clear = new JButton("Clear");
    private final transient List<Member> allMembers = new ArrayList<>();
    private final transient List<Book> allBooks = new ArrayList<>();
    private final transient List<Book> visibleBooks = new ArrayList<>();
    private final transient List<CartEntry> cart = new ArrayList<>();
    private transient Member selectedMember;
    private transient Book selectedBook;
    private boolean refreshing;

    public LendingPanel(User actor, CatalogService catalog, MemberService members, LendingService lending,
            ReservationService reservations) throws IOException {
        this.actor = actor;
        this.catalog = catalog;
        this.members = members;
        this.lending = lending;
        this.reservations = reservations;
        build();
        refresh();
    }

    private void build() {
        setLayout(new BorderLayout(Theme.GAP, Theme.GAP));
        setBackground(Theme.WHITE);
        add(buildWorkspace(), BorderLayout.CENTER);
        add(status, BorderLayout.SOUTH);
        status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        status.setForeground(Theme.MUTED);
    }

    private JSplitPane buildWorkspace() {
        JSplitPane selection = new JSplitPane(JSplitPane.VERTICAL_SPLIT, buildMemberPane(), buildBookPane());
        selection.setResizeWeight(0.30);
        selection.setBorder(null);
        JSplitPane workspace = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, selection, buildCartPane());
        workspace.setResizeWeight(0.62);
        workspace.setBorder(null);
        workspace.setMinimumSize(new Dimension(700, 420));
        return workspace;
    }

    private JPanel buildMemberPane() {
        JPanel pane = titledPane("1. Choose a member");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.setOpaque(false);
        JLabel label = new JLabel("Search members:");
        label.setLabelFor(memberSearch);
        controls.add(label);
        controls.add(memberSearch);
        pane.add(controls, BorderLayout.NORTH);
        memberCard.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        pane.add(memberCard, BorderLayout.CENTER);
        memberList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        memberList.setCellRenderer(new MemberRenderer());
        memberList.setVisibleRowCount(3);
        memberList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !refreshing) selectMember(memberList.getSelectedValue());
        });
        JScrollPane list = new JScrollPane(memberList);
        list.setBorder(BorderFactory.createTitledBorder("Members"));
        pane.add(list, BorderLayout.SOUTH);
        memberSearch.getDocument().addDocumentListener(documentListener(this::filterMembers));
        return pane;
    }

    private JPanel buildBookPane() {
        JPanel pane = titledPane("2. Choose a book");
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        controls.setOpaque(false);
        JLabel label = new JLabel("Search books:");
        label.setLabelFor(bookSearch);
        controls.add(label);
        controls.add(bookSearch);
        controls.add(bookFilter);
        bookCard.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        bookTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        bookTable.setAutoCreateRowSorter(true);
        bookTable.setFillsViewportHeight(true);
        bookTable.setRowHeight(25);
        bookTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) selectBook();
        });
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(controls, BorderLayout.NORTH);
        header.add(bookCard, BorderLayout.SOUTH);
        pane.add(header, BorderLayout.NORTH);
        pane.add(new JScrollPane(bookTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        addBook.setToolTipText("Add the selected available book to the lending list");
        reserveBook.setToolTipText("Reserve the selected unavailable book for this member");
        addBook.addActionListener(event -> addSelectedBook());
        reserveBook.addActionListener(event -> reserveSelectedBook());
        actions.add(addBook);
        actions.add(reserveBook);
        pane.add(actions, BorderLayout.SOUTH);
        bookSearch.getDocument().addDocumentListener(documentListener(this::filterBooks));
        bookFilter.addActionListener(event -> filterBooks());
        return pane;
    }

    private JPanel buildCartPane() {
        JPanel pane = titledPane("3. Review and confirm");
        pane.setPreferredSize(new Dimension(300, 0));
        pane.add(cartSummary, BorderLayout.NORTH);
        cartTable.setFillsViewportHeight(true);
        cartTable.setRowHeight(27);
        cartTable.getModel().addTableModelListener(this::cartChanged);
        pane.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        JPanel actions = new JPanel(new GridLayout(0, 1, 5, 5));
        actions.setOpaque(false);
        JButton remove = new JButton("Remove selected");
        remove.setToolTipText("Remove the selected book from this lending list");
        remove.addActionListener(event -> removeSelectedCartItem());
        clear.setToolTipText("Clear all books from this lending list");
        clear.addActionListener(event -> clearCart());
        confirm.setToolTipText("Save this lending transaction");
        confirm.addActionListener(event -> confirmLending());
        actions.add(remove);
        actions.add(clear);
        actions.add(confirm);
        pane.add(actions, BorderLayout.SOUTH);
        return pane;
    }

    private JPanel titledPane(String title) {
        JPanel pane = new JPanel(new BorderLayout(5, 5));
        pane.setBackground(Theme.WHITE);
        pane.setBorder(BorderFactory.createTitledBorder(title));
        return pane;
    }

    public void refresh() throws IOException {
        String memberId = selectedMember == null ? "" : selectedMember.memberId();
        refreshing = true;
        try {
            allMembers.clear();
            allMembers.addAll(members.listSorted(actor));
            allBooks.clear();
            allBooks.addAll(catalog.loadBooks());
            filterMembers();
            filterBooks();
            restoreMember(memberId);
            updateBookActions();
            updateCartSummary();
        } finally {
            refreshing = false;
        }
    }

    private void filterMembers() {
        String query = memberSearch.getText().trim().toLowerCase(Locale.ROOT);
        Member current = selectedMember;
        memberModel.clear();
        allMembers.stream().filter(member -> query.isEmpty() || member.name().toLowerCase(Locale.ROOT).contains(query)
                || member.memberId().toLowerCase(Locale.ROOT).contains(query)
                || member.phone().toLowerCase(Locale.ROOT).contains(query)).forEach(memberModel::addElement);
        if (current != null) memberList.setSelectedValue(current, true);
        if (memberModel.isEmpty()) memberCard.setText("<html><b>No members found.</b><br>Adjust the search or add a member from Members.</html>");
    }

    private void filterBooks() {
        String query = bookSearch.getText().trim().toLowerCase(Locale.ROOT);
        boolean availableOnly = "Available only".equals(bookFilter.getSelectedItem());
        bookModel.setRowCount(0);
        visibleBooks.clear();
        allBooks.stream().filter(book -> !availableOnly || lending.lendableCopies(book.bookId()) > 0)
                .filter(book -> query.isEmpty() || searchable(book).toLowerCase(Locale.ROOT).contains(query))
                .forEach(book -> {
                    visibleBooks.add(book);
                    bookModel.addRow(new Object[] { book.title(), book.authors(), book.category(), book.isbn(),
                            availability(book), book.shelfLocation() });
                });
        if (bookModel.getRowCount() == 0) bookCard.setText("<html><b>No books match this search.</b><br>Adjust the search or add a book from Book-keeping.</html>");
        updateBookActions();
    }

    private void selectMember(Member member) {
        if (member == null || member == selectedMember) return;
        if (!cart.isEmpty() && selectedMember != null) {
            int choice = JOptionPane.showConfirmDialog(this, "Clear the current lending list and choose " + member.name() + "?",
                    "Change member", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                refreshing = true;
                memberList.setSelectedValue(selectedMember, true);
                refreshing = false;
                return;
            }
            clearCartData();
        }
        selectedMember = member;
        updateMemberCard();
        updateBookActions();
        updateCartSummary();
    }

    private void selectBook() {
        int row = bookTable.getSelectedRow();
        selectedBook = row < 0 ? null : bookAt(row);
        updateBookCard();
        updateBookActions();
    }

    private void addSelectedBook() {
        if (selectedMember == null) { status.setText("Choose a member before adding a book."); return; }
        if (selectedBook == null) { status.setText("Choose a book first."); return; }
        if (lending.lendableCopies(selectedBook.bookId()) < 1) { status.setText("No lendable copy; use Reserve selected."); return; }
        if (cart.stream().anyMatch(entry -> entry.bookId().equals(selectedBook.bookId()))) {
            status.setText("That book is already in the lending list.");
            return;
        }
        cart.add(new CartEntry(selectedBook.bookId(), selectedBook.title(), lending.defaultDueDate()));
        renderCart();
        status.setText(selectedBook.title() + " added to the lending list.");
    }

    private void reserveSelectedBook() {
        if (selectedMember == null || selectedBook == null) {
            status.setText("Choose a member and a book first.");
            return;
        }
        try {
            reservations.reserve(actor, selectedMember.memberId(), selectedBook.bookId());
            status.setText("Reservation added for " + selectedMember.name() + ".");
            refresh();
        } catch (Exception exception) {
            status.setText(message(exception));
        }
    }

    private void removeSelectedCartItem() {
        int row = cartTable.getSelectedRow();
        if (row < 0) { status.setText("Select a book in the lending list first."); return; }
        cart.remove(row);
        renderCart();
    }

    private void clearCart() {
        if (cart.isEmpty()) return;
        if (JOptionPane.showConfirmDialog(this, "Clear all books from the lending list?", "Clear lending list",
                JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) clearCartData();
    }

    private void clearCartData() {
        cart.clear();
        renderCart();
    }

    private void confirmLending() {
        if (selectedMember == null || cart.isEmpty()) {
            status.setText("Choose a member and add at least one book.");
            return;
        }
        List<LendingService.LendingRequest> requests = new ArrayList<>();
        for (CartEntry entry : cart) {
            try {
                requests.add(new LendingService.LendingRequest(entry.bookId(), LocalDate.parse(entry.dueDate())));
            } catch (RuntimeException exception) {
                status.setText("Due date for " + entry.title() + " must use YYYY-MM-DD format.");
                return;
            }
        }
        confirm.setEnabled(false);
        try {
            lending.lend(actor, selectedMember.memberId(), requests);
            String name = selectedMember.name();
            clearCartData();
            refresh();
            status.setText("Lending completed for " + name + ".");
        } catch (Exception exception) {
            status.setText(message(exception));
        } finally {
            confirm.setEnabled(!cart.isEmpty() && selectedMember != null);
        }
    }

    private void renderCart() {
        cartModel.setRowCount(0);
        cart.forEach(entry -> cartModel.addRow(new Object[] { entry.title(), entry.dueDate() }));
        updateCartSummary();
    }

    private void cartChanged(javax.swing.event.TableModelEvent event) {
        if (event.getType() != TableModelEvent.UPDATE || event.getColumn() != 1
                || event.getFirstRow() < 0 || event.getFirstRow() >= cart.size()) {
            updateCartSummary();
            return;
        }
        int row = event.getFirstRow();
        String raw = String.valueOf(cartModel.getValueAt(row, 1)).trim();
        try {
            LocalDate.parse(raw);
            CartEntry old = cart.get(row);
            cart.set(row, new CartEntry(old.bookId(), old.title(), LocalDate.parse(raw)));
            status.setText("Due date updated for " + old.title() + ".");
        } catch (RuntimeException exception) {
            refreshing = true;
            cartModel.setValueAt(cart.get(row).dueDate(), row, 1);
            refreshing = false;
            status.setText("Due dates must use YYYY-MM-DD format.");
        }
        updateCartSummary();
    }

    private void updateCartSummary() {
        String memberText = selectedMember == null ? "No member selected." : "Member: " + selectedMember.name()
                + " (" + selectedMember.memberId() + ")";
        cartSummary.setText("<html>" + memberText + "<br>" + cart.size() + " book(s) selected.</html>");
        confirm.setEnabled(selectedMember != null && !cart.isEmpty());
        clear.setEnabled(!cart.isEmpty());
    }

    private void updateMemberCard() {
        if (selectedMember == null) {
            memberCard.setText("<html><b>Select a member</b><br>Members available for lending will appear below.</html>");
            return;
        }
        int borrowed = lending.activeLoansForMember(selectedMember.memberId()).size();
        int limit = lending.borrowingLimit(selectedMember);
        String warning = selectedMember.status() != model.MemberStatus.ACTIVE ? "<br><b>Status: " + selectedMember.status() + "</b>" : "";
        memberCard.setText("<html><b>" + selectedMember.name() + "</b> · " + selectedMember.memberId()
                + "<br>" + selectedMember.membershipType() + " · Expires " + selectedMember.expiryDate()
                + "<br>Borrowed " + borrowed + " of " + limit + warning + "</html>");
    }

    private void updateBookCard() {
        if (selectedBook == null) {
            bookCard.setText("<html><b>Select a book</b><br>Book details will appear here.</html>");
            return;
        }
        bookCard.setText("<html><b>" + selectedBook.title() + "</b> · " + selectedBook.authors()
                + "<br>" + selectedBook.category() + " · ISBN " + selectedBook.isbn()
                + " · Shelf " + selectedBook.shelfLocation() + " · " + availability(selectedBook) + "</html>");
    }

    private void updateBookActions() {
        boolean hasSelection = selectedMember != null && selectedBook != null;
        boolean available = hasSelection && lending.lendableCopies(selectedBook.bookId()) > 0;
        addBook.setEnabled(available);
        reserveBook.setEnabled(hasSelection && !available);
        addBook.setToolTipText(selectedMember == null ? "Choose a member first"
                : selectedBook == null ? "Choose a book first" : available ? "Add the selected available book" : "No lendable copy available");
        reserveBook.setToolTipText(hasSelection && !available ? "Reserve the selected unavailable book" : "Select an unavailable book to reserve");
    }

    private void restoreMember(String memberId) {
        if (!memberId.isEmpty()) {
            allMembers.stream().filter(member -> member.memberId().equals(memberId)).findFirst().ifPresent(member -> {
                selectedMember = member;
                memberList.setSelectedValue(member, true);
                updateMemberCard();
            });
        }
    }

    private Book bookAt(int viewRow) {
        int modelRow = bookTable.convertRowIndexToModel(viewRow);
        return modelRow >= 0 && modelRow < visibleBooks.size() ? visibleBooks.get(modelRow) : null;
    }

    private String searchable(Book book) {
        return String.join(" ", book.title(), book.authors(), book.category(), book.isbn(), book.shelfLocation());
    }

    private String availability(Book book) {
        int lendable = lending.lendableCopies(book.bookId());
        int held = Math.max(0, book.availableCopies() - lendable);
        return lendable + " / " + book.totalCopies() + (held > 0 ? " (" + held + " held)" : "");
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? "The operation could not be completed." : exception.getMessage();
    }

    private static javax.swing.event.DocumentListener documentListener(Runnable action) {
        return new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { action.run(); }
        };
    }

    private record CartEntry(String bookId, String title, LocalDate dueDateValue) {
        String dueDate() { return dueDateValue.toString(); }
    }

    private static final class MemberRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        @Override public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(list, value, index, selected, focus);
            if (value instanceof Member member) setText(member.name() + "  ·  " + member.memberId());
            return this;
        }
    }
}
