import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import model.Book;
import ui.LendBookPickerPanel;

public final class LendBookPickerPanelTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        SwingUtilities.invokeAndWait(() -> {
            LendBookPickerPanel panel = new LendBookPickerPanel();
            panel.setSize(560, 380);
            panel.doLayout();
            Book book = new Book("book-id", Book.OwnershipType.OWNED, "", "9780000000001",
                    "Sample book", "Sample author", "", "", "", "", "", BigDecimal.ZERO,
                    1, 1, Book.Condition.GOOD, Book.ReadingStatus.UNREAD, "", LocalDate.now(), false);
            panel.setBooks(List.of(book));
            JTable table = find(panel, JTable.class);
            check(table.getPreferredScrollableViewportSize().height >= 240);
            table.setRowSelectionInterval(0, 0);
            JButton add = findButton(panel, "Add selected book");
            check(add.isEnabled());
            AtomicReference<Book> added = new AtomicReference<>();
            panel.setAddHandler(added::set);
            add.doClick();
            check(added.get() == book);
        });
        System.out.println("LendBookPickerPanelTest passed");
    }

    private static <T extends Component> T find(Container parent, Class<T> type) {
        for (Component component : parent.getComponents()) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
            if (component instanceof Container child) {
                T match = find(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        throw new AssertionError("Expected component was not found: " + type.getSimpleName());
    }

    private static JButton findButton(Container parent, String text) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                try {
                    return findButton(child, text);
                } catch (AssertionError ignored) {
                    // Continue through sibling components.
                }
            }
        }
        throw new AssertionError("Add selected book button was not found");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("Lend book picker assertion failed");
        }
    }
}