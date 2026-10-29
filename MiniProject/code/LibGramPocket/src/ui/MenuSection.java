package ui;

import java.util.List;

public enum MenuSection {
    BOOKKEEPING("Book-keeping", List.of("Add book", "Update book", "Remove book", "View details")),
    LENDING("Lend a book", List.of("New lend", "Receive a return", "Active lends", "Lend history", "Contacts")),
    BORROWING("Borrow a book",
            List.of("New borrow", "Return to lender", "Active borrows", "Borrow history", "Contacts"));

    private final String label;
    private final List<String> options;

    MenuSection(String label, List<String> options) {
        this.label = label;
        this.options = options;
    }

    public String label() {
        return label;
    }

    public List<String> options() {
        return options;
    }
}