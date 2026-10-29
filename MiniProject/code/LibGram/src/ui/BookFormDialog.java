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
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import model.Book;
import model.User;
import services.CatalogService;
import services.DuplicateIsbnException;

public final class BookFormDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final String[] FIELD_NAMES = {
            "ISBN", "Title", "Author(s)", "Publisher", "Publication year", "Edition", "Category", "Language",
            "Shelf location", "Replacement price (Rs.)", "Total copies" };

    private final transient User actor;
    private final transient CatalogService catalogService;
    private final transient Book existing;
    private final transient Consumer<Book> onOpenExisting;
    private final transient Map<String, JTextField> fields = new LinkedHashMap<>();
    private final JLabel errorMessage = new JLabel(" ");
    private final JButton openExistingButton = new JButton("Open existing record");
    private transient Book savedBook;

    public BookFormDialog(Window owner, User actor, CatalogService catalogService, Book existing,
            Consumer<Book> onOpenExisting) {
        super(owner, existing == null ? "Add book" : "Update book", Dialog.ModalityType.APPLICATION_MODAL);
        this.actor = actor;
        this.catalogService = catalogService;
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
        setMinimumSize(new java.awt.Dimension(620, 610));
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(5, 0, 5, 12);
        for (String name : FIELD_NAMES) {
            JTextField field = new JTextField(26);
            field.setName(name);
            field.setText(valueFor(name));
            fields.put(name, field);
            JLabel label = new JLabel(name);
            label.setLabelFor(field);
            label.setForeground(Theme.INK);
            form.add(label, constraints);
            constraints.gridx = 1;
            constraints.insets = new Insets(5, 0, 5, 0);
            form.add(field, constraints);
            constraints.gridx = 0;
            constraints.gridy++;
            constraints.insets = new Insets(5, 0, 5, 12);
        }

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
        openExistingButton.addActionListener(event -> duplicateRecord.ifPresent(book -> {
            onOpenExisting.accept(book);
            dispose();
        }));
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
        fields.get("Total copies").addActionListener(event -> saveBook());
        buttons.add(cancel);
        buttons.add(save);
        footer.add(buttons, footerConstraints);

        add(new JScrollPane(form), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setSize(Math.max(getWidth(), 620), Math.max(getHeight(), 610));
        setLocationRelativeTo(getOwner());
    }

    private transient Optional<Book> duplicateRecord = Optional.empty();

    private void saveBook() {
        errorMessage.setText(" ");
        openExistingButton.setVisible(false);
        duplicateRecord = Optional.empty();
        try {
            Book draft = buildDraft();
            savedBook = existing == null ? catalogService.addBook(actor, draft)
                    : catalogService.updateBook(actor, draft);
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
        BigDecimal price;
        int totalCopies;
        try {
            price = new BigDecimal(fields.get("Replacement price (Rs.)").getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a valid replacement price.");
        }
        try {
            totalCopies = Integer.parseInt(fields.get("Total copies").getText().trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Enter a whole number for total copies.");
        }
        return new Book(existing == null ? "" : existing.bookId(), value("ISBN"), value("Title"),
                value("Author(s)"), value("Publisher"), value("Publication year"), value("Edition"),
                value("Category"), value("Language"), value("Shelf location"), price, totalCopies,
                existing == null ? totalCopies : existing.availableCopies(),
                existing == null ? LocalDate.now() : existing.dateAdded());
    }

    private String valueFor(String name) {
        if (existing == null) {
            return name.equals("Total copies") ? "1" : "";
        }
        return switch (name) {
            case "ISBN" -> existing.isbn();
            case "Title" -> existing.title();
            case "Author(s)" -> existing.authors();
            case "Publisher" -> existing.publisher();
            case "Publication year" -> existing.publicationYear();
            case "Edition" -> existing.edition();
            case "Category" -> existing.category();
            case "Language" -> existing.language();
            case "Shelf location" -> existing.shelfLocation();
            case "Replacement price (Rs.)" -> existing.price().toPlainString();
            case "Total copies" -> Integer.toString(existing.totalCopies());
            default -> "";
        };
    }

    private String value(String name) {
        return fields.get(name).getText().trim();
    }
}
