package ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import model.Book;
import model.Book.Condition;
import model.Book.OwnershipType;
import model.Book.ReadingStatus;
import services.BookService;
import services.DuplicateIsbnException;

public final class BookFormDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final String[] TEXT_FIELDS = {
            "ISBN", "Title", "Authors", "Publisher", "Publication year", "Edition", "Category", "Language",
            "Price (Rs.)", "Copies owned" };

    private final transient BookService bookService;
    private final transient Book existing;
    private final transient Consumer<Book> onOpenExisting;
    private final transient Map<String, JTextField> fields = new LinkedHashMap<>();
    private final JTextArea notes = new JTextArea(4, 28);
    private final JComboBox<Condition> condition = new JComboBox<>(Condition.values());
    private final JComboBox<ReadingStatus> readingStatus = new JComboBox<>(ReadingStatus.values());
    private final JLabel errorMessage = new JLabel(" ");
    private final JButton openExistingButton = new JButton("Open existing record");
    private transient Optional<Book> duplicateRecord = Optional.empty();
    private transient Book savedBook;

    public BookFormDialog(Window owner, BookService bookService, Book existing, Consumer<Book> onOpenExisting) {
        super(owner, existing == null ? "Add book" : "Update book", Dialog.ModalityType.APPLICATION_MODAL);
        this.bookService = bookService;
        this.existing = existing;
        this.onOpenExisting = onOpenExisting;
        buildDialog();
    }

    public Optional<Book> showDialog() {
        setVisible(true);
        return Optional.ofNullable(savedBook);
    }

    private void buildDialog() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 0, 5, 12);
        for (String name : TEXT_FIELDS) {
            if (existing != null && existing.ownershipType() == OwnershipType.BORROWED
                    && name.equals("Copies owned")) {
                continue;
            }
            JTextField field = new JTextField(28);
            field.setName(name);
            field.setText(valueFor(name));
            fields.put(name, field);
            addRow(form, constraints, name, field);
        }
        condition.setSelectedItem(existing == null ? Condition.GOOD : existing.condition());
        addRow(form, constraints, "Condition", condition);
        readingStatus.setSelectedItem(existing == null ? ReadingStatus.UNREAD : existing.readingStatus());
        addRow(form, constraints, "Reading status", readingStatus);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setText(existing == null ? "" : existing.notes());
        notes.setName("Notes");
        addRow(form, constraints, "Notes", new JScrollPane(notes));

        JPanel footer = new JPanel(new GridBagLayout());
        footer.setBackground(Theme.WHITE);
        GridBagConstraints footerConstraints = new GridBagConstraints();
        footerConstraints.gridx = 0;
        footerConstraints.gridy = 0;
        footerConstraints.gridwidth = 2;
        footerConstraints.anchor = GridBagConstraints.WEST;
        footerConstraints.insets = new Insets(4, 10, 4, 10);
        errorMessage.setForeground(Theme.ERROR);
        footer.add(errorMessage, footerConstraints);
        footerConstraints.gridy++;
        openExistingButton.setVisible(false);
        openExistingButton.addActionListener(event -> duplicateRecord.ifPresent(onOpenExisting));
        footer.add(openExistingButton, footerConstraints);
        footerConstraints.gridy++;
        footerConstraints.anchor = GridBagConstraints.EAST;
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton save = new JButton(existing == null ? "Add book" : "Save changes");
        save.setBackground(Theme.ACCENT);
        save.setForeground(Theme.WHITE);
        save.addActionListener(event -> saveBook());
        fields.get("Copies owned").addActionListener(event -> saveBook());
        buttons.add(cancel);
        buttons.add(save);
        footer.add(buttons, footerConstraints);

        add(new JScrollPane(form), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setSize(Math.max(getWidth(), 620), Math.max(getHeight(), 680));
        setLocationRelativeTo(getOwner());
    }

    private void saveBook() {
        errorMessage.setText(" ");
        openExistingButton.setVisible(false);
        duplicateRecord = Optional.empty();
        try {
            Book draft = buildDraft();
            savedBook = existing == null ? bookService.add(draft) : bookService.update(draft);
            dispose();
        } catch (DuplicateIsbnException exception) {
            duplicateRecord = Optional.of(exception.existingBook());
            errorMessage.setText(exception.getMessage());
            openExistingButton.setVisible(true);
            pack();
        } catch (IllegalArgumentException exception) {
            errorMessage.setText(exception.getMessage());
        } catch (IOException exception) {
            System.getLogger(BookFormDialog.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to save book", exception);
            errorMessage.setText("The book could not be saved. Check local storage and try again.");
        }
    }

    private Book buildDraft() {
        BigDecimal price = parsePrice(fields.get("Price (Rs.)").getText());
        int copies = 1;
        if (existing == null || existing.ownershipType() == OwnershipType.OWNED) {
            try {
                copies = Integer.parseInt(fields.get("Copies owned").getText().trim());
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Copies owned must be a whole number.");
            }
        }
        OwnershipType ownership = existing == null ? OwnershipType.OWNED : existing.ownershipType();
        String lenderId = existing == null ? "" : existing.lenderContactId();
        return new Book(existing == null ? "" : existing.bookId(), ownership, lenderId, value("ISBN"),
                value("Title"), value("Authors"), value("Publisher"), value("Publication year"),
                value("Edition"), value("Category"), value("Language"), price, copies,
                existing == null || ownership == OwnershipType.BORROWED ? copies : existing.availableCopies(),
                (Condition) condition.getSelectedItem(),
                (ReadingStatus) readingStatus.getSelectedItem(), notes.getText(),
                existing == null ? LocalDate.now() : existing.dateAdded(),
                existing != null && existing.archived());
    }

    private static BigDecimal parsePrice(String value) {
        if (value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid non-negative price, or leave it blank.");
        }
    }

    private String valueFor(String name) {
        if (existing == null) {
            return name.equals("Copies owned") ? "1" : "";
        }
        return switch (name) {
            case "ISBN" -> existing.isbn();
            case "Title" -> existing.title();
            case "Authors" -> existing.authors();
            case "Publisher" -> existing.publisher();
            case "Publication year" -> existing.publicationYear();
            case "Edition" -> existing.edition();
            case "Category" -> existing.category();
            case "Language" -> existing.language();
            case "Price (Rs.)" -> existing.price().toPlainString();
            case "Copies owned" -> Integer.toString(existing.totalCopies());
            default -> "";
        };
    }

    private String value(String name) {
        return fields.get(name).getText().trim();
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints, String name,
            java.awt.Component input) {
        JLabel label = new JLabel(name);
        label.setLabelFor(input);
        label.setForeground(Theme.INK);
        panel.add(label, constraints);
        constraints.gridx = 1;
        constraints.insets = new Insets(5, 0, 5, 0);
        panel.add(input, constraints);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.insets = new Insets(5, 0, 5, 12);
    }
}