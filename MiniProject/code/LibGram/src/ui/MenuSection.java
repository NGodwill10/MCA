package ui;

import java.util.List;

public enum MenuSection {
    BOOKKEEPING("Book-keeping", List.of("Add book", "Update book", "Remove book", "View details")),
    MEMBERS("Members", List.of("Add member", "Update member", "Remove member", "View member details")),
    LENDING("Lend a book", List.of("New lending", "Reservations", "Collect dues", "Active loans")),
    FEES_PAYMENTS("Fees & Payments", List.of("Collect payment", "Payment history")),
    RECOVERY("Recover a book", List.of("Return a book", "Mark lost", "Replace member card", "Overdue list"));

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
