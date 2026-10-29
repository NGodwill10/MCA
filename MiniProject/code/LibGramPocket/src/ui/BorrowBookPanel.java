package ui;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import model.Book;
import model.Contact;
import model.Loan;
import model.PricingTerms;
import model.Book.Condition;
import model.Book.OwnershipType;
import model.Book.ReadingStatus;
import services.BookSearchField;
import services.BookService;
import services.ContactService;
import services.DuplicateIsbnException;
import services.LendingService;
import services.SettingsService;

public final class BorrowBookPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final List<String> FIELD_NAMES = List.of("ISBN", "Title", "Authors", "Publisher",
            "Publication year", "Edition", "Category", "Language", "Price (Rs.)");

    private final transient BookService books;
    private final transient LendingService lending;
    private final ContactPicker lenderPicker;
    private final transient Map<String, JTextField> fields = new LinkedHashMap<>();
    private final JTextArea notes = new JTextArea(3, 24);
    private final JComboBox<Condition> condition = new JComboBox<>(Condition.values());
    private final JComboBox<ReadingStatus> readingStatus = new JComboBox<>(ReadingStatus.values());
    private final JTextField dueDate = new JTextField(12);
    private final JLabel status = new JLabel("Choose a lender and enter the borrowed book details.");
    private final PricingForm pricing;
    private transient Runnable completed = () -> {
    };

    public BorrowBookPanel(BookService books, ContactService contacts, LendingService lending,
            SettingsService settings) {
        this.books = books;
        this.lending = lending;
        lenderPicker = new ContactPicker(contacts);
        pricing = new PricingForm(settings, settings.pricingFor("borrow"));
        dueDate.setText(LocalDate.now().plusDays(settings.getInt("loan.defaultDays")).toString());
        buildPanel();
    }

    public void setCompletedHandler(Runnable handler) {
        completed = handler == null ? () -> {
        } : handler;
    }

    public void setPricingTerms(PricingTerms terms) {
        pricing.setTerms(terms);
    }

    private void buildPanel() {
        setLayout(new BorderLayout(0, Theme.GAP));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.WHITE);
        form.setBorder(BorderFactory.createTitledBorder("Book details"));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 6, 4, 10);
        for (String name : FIELD_NAMES) {
            JTextField input = new JTextField(22);
            input.setName(name);
            fields.put(name, input);
            addRow(form, constraints, name, input);
        }
        condition.setSelectedItem(Condition.GOOD);
        addRow(form, constraints, "Condition", condition);
        addRow(form, constraints, "Reading status", readingStatus);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        addRow(form, constraints, "Notes", new JScrollPane(notes));
        JButton fillExisting = new JButton("Fill from existing record");
        fillExisting.addActionListener(event -> fillFromExisting());
        addRow(form, constraints, "ISBN match", fillExisting);

        JPanel details = new JPanel();
        details.setBackground(Theme.WHITE);
        details.setLayout(new javax.swing.BoxLayout(details, javax.swing.BoxLayout.Y_AXIS));
        details.add(form);
        details.add(pricing);

        JPanel panes = new JPanel(new GridLayout(2, 1, 0, Theme.GAP));
        panes.setBackground(Theme.WHITE);
        panes.add(lenderPicker);
        panes.add(new JScrollPane(details));
        add(panes, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(8, 0));
        actions.setOpaque(false);
        JPanel due = new JPanel(new BorderLayout(6, 0));
        due.setOpaque(false);
        JLabel dueLabel = new JLabel("Due date (YYYY-MM-DD)");
        dueLabel.setLabelFor(dueDate);
        due.add(dueLabel, BorderLayout.WEST);
        due.add(dueDate, BorderLayout.CENTER);
        actions.add(due, BorderLayout.WEST);
        status.setForeground(Theme.MUTED);
        actions.add(status, BorderLayout.CENTER);
        JButton confirm = new JButton("Confirm borrow");
        confirm.setBackground(Theme.ACCENT);
        confirm.setForeground(Theme.WHITE);
        confirm.addActionListener(event -> confirmBorrow());
        actions.add(confirm, BorderLayout.EAST);
        add(actions, BorderLayout.SOUTH);
    }

    private void fillFromExisting() {
        String isbn = value("ISBN");
        if (isbn.isBlank()) {
            status.setText("Enter an ISBN to search existing catalog records.");
            return;
        }
        try {
            List<Book> matches = books.search(BookSearchField.ISBN, isbn, false, true);
            if (matches.isEmpty()) {
                status.setText("No catalog record matches that ISBN.");
                return;
            }
            Book match = matches.getFirst();
            fields.get("Title").setText(match.title());
            fields.get("Authors").setText(match.authors());
            fields.get("Publisher").setText(match.publisher());
            fields.get("Publication year").setText(match.publicationYear());
            fields.get("Edition").setText(match.edition());
            fields.get("Category").setText(match.category());
            fields.get("Language").setText(match.language());
            fields.get("Price (Rs.)").setText(match.price().toPlainString());
            condition.setSelectedItem(match.condition());
            readingStatus.setSelectedItem(match.readingStatus());
            notes.setText(match.notes());
            status.setText("Filled details from " + match.title() + ".");
        } catch (IOException exception) {
            log(exception);
            status.setText("Catalog details could not be loaded.");
        }
    }

    private void confirmBorrow() {
        Contact lender = lenderPicker.getSelectedContact().orElse(null);
        if (lender == null) {
            status.setText("Choose a lender before confirming.");
            return;
        }
        try {
            LocalDate due = LocalDate.parse(dueDate.getText().trim());
            PricingTerms terms = pricing.getTerms();
            Book borrowedBook = books.prepareBorrowedBook(buildDraft(), lender.contactId());
            String summary = "Lender: " + lender.name() + "\nBook: " + borrowedBook.title()
                    + " by " + borrowedBook.authors() + "\nDue date: " + due
                    + "\n\nPricing:\n" + pricingSummary(terms);
            if (JOptionPane.showConfirmDialog(this, summary, "Confirm borrow", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            Loan loan = lending.borrowNewBook(borrowedBook, due, terms);
            status.setText("Borrowed " + loan.bookTitleSnapshot() + " from " + lender.name() + ".");
            completed.run();
        } catch (DuplicateIsbnException exception) {
            status.setText(exception.getMessage() + " Use Fill from existing record to copy its details.");
        } catch (IllegalArgumentException exception) {
            status.setText(exception.getMessage());
        } catch (IOException exception) {
            log(exception);
            JOptionPane.showMessageDialog(this, "The borrow could not be saved. Check local storage and try again.",
                    "Borrow", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Book buildDraft() {
        BigDecimal price;
        try {
            String value = value("Price (Rs.)");
            price = value.isBlank() ? BigDecimal.ZERO : new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid non-negative price, or leave it blank.");
        }
        return new Book("", OwnershipType.OWNED, "", value("ISBN"), value("Title"), value("Authors"),
                value("Publisher"), value("Publication year"), value("Edition"), value("Category"),
                value("Language"), price, 1, 1, (Condition) condition.getSelectedItem(),
                (ReadingStatus) readingStatus.getSelectedItem(), notes.getText(), LocalDate.now(), false);
    }

    private String value(String name) {
        return fields.get(name).getText().trim();
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints, String name,
            java.awt.Component input) {
        JLabel label = new JLabel(name);
        label.setLabelFor(input);
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.insets = new Insets(4, 0, 4, 6);
        panel.add(input, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.weightx = 0;
        constraints.insets = new Insets(4, 6, 4, 10);
    }

    private static String pricingSummary(PricingTerms terms) {
        return "Base: " + (terms.baseEnabled() ? DisplayFormat.money(terms.baseAmount()) : "Off")
                + "\nRent: " + (terms.rentEnabled()
                        ? DisplayFormat.money(terms.rentAmount()) + " per " + terms.rentUnit().name().toLowerCase()
                        : "Off")
                + "\nOverdue penalty: " + (terms.penaltyEnabled()
                        ? DisplayFormat.money(terms.penaltyPerDay()) + " per overdue day"
                        : "Off");
    }

    private static void log(Exception exception) {
        System.getLogger(BorrowBookPanel.class.getName()).log(System.Logger.Level.ERROR,
                "Borrow form operation failed", exception);
    }
}