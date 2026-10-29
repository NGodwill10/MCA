import data.CsvBookRepository;
import data.CsvContactRepository;
import data.CsvLoanRepository;
import data.CsvSettingsRepository;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import services.BookService;
import services.ContactService;
import services.LendingService;
import services.SettingsService;
import ui.BorrowBookPanel;
import ui.ContactPicker;

public final class BorrowBookPanelLayoutTest {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path root = Files.createTempDirectory("libgrampocket-borrow-layout-");
        try {
            var books = new CsvBookRepository(root.resolve("books.txt"));
            var contacts = new CsvContactRepository(root.resolve("contacts.txt"));
            var loans = new CsvLoanRepository(root.resolve("loans.txt"));
            var settings = new SettingsService(new CsvSettingsRepository(root.resolve("settings.txt")));
            var contactService = new ContactService(contacts, loans);
            var lending = new LendingService(books, loans, contacts, settings);
            SwingUtilities.invokeAndWait(() -> {
                BorrowBookPanel panel = new BorrowBookPanel(new BookService(books, loans, contacts),
                        contactService, lending, settings);
                panel.setSize(800, 900);
                panel.doLayout();
                JPanel panes = findPanes(panel);
                panes.doLayout();
                Component[] children = panes.getComponents();
                check(children[0] instanceof ContactPicker);
                check(children[1] instanceof JScrollPane);
                check(children[0].getHeight() == children[1].getHeight());
            });
            System.out.println("BorrowBookPanelLayoutTest passed");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static JPanel findPanes(Container parent) {
        for (Component component : parent.getComponents()) {
            if (component instanceof JPanel panel && panel.getLayout() instanceof GridLayout grid
                    && grid.getRows() == 2 && panel.getComponentCount() == 2
                    && panel.getComponent(0) instanceof ContactPicker
                    && panel.getComponent(1) instanceof JScrollPane) {
                return panel;
            }
            if (component instanceof Container child) {
                try {
                    return findPanes(child);
                } catch (AssertionError ignored) {
                    // Continue through sibling components.
                }
            }
        }
        throw new AssertionError("Equal-height New borrow panes were not found");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("New borrow panes do not have equal heights");
        }
    }
}