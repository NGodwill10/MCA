package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import model.Book;
import model.PricingTerms;
import services.LendingService;
import services.SettingsService;

public final class LendCartPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient SettingsService settings;
    private final JPanel entries = new JPanel();
    private final JLabel heading = new JLabel("Lend list (0)");
    private final JLabel preview = new JLabel();
    private final JLabel status = new JLabel("Select a contact, add books, then confirm.");
    private final PricingForm pricingForm;
    private final transient Map<String, CartEntry> books = new LinkedHashMap<>();
    private transient Runnable confirmHandler = () -> {
    };

    public LendCartPanel(SettingsService settings, PricingTerms initialTerms) {
        this.settings = settings;
        pricingForm = new PricingForm(settings, initialTerms);
        buildPanel();
        pricingForm.setValidityChanged(this::updatePreview);
    }

    public void setConfirmHandler(Runnable handler) {
        confirmHandler = handler == null ? () -> {
        } : handler;
    }

    public boolean addBook(Book book) {
        if (books.containsKey(book.bookId())) {
            setStatus("That book is already in the lend list.");
            return false;
        }
        LocalDate dueDate = LocalDate.now().plusDays(settings.getInt("loan.defaultDays"));
        CartEntry entry = new CartEntry(book, dueDate);
        books.put(book.bookId(), entry);
        entries.add(entry.panel());
        refreshCount();
        setStatus("Added \"" + book.title() + "\".");
        return true;
    }

    public List<LendingService.LendRequest> requests() {
        List<LendingService.LendRequest> result = new ArrayList<>();
        for (CartEntry entry : books.values()) {
            LocalDate dueDate;
            try {
                dueDate = LocalDate.parse(entry.dueDate().getText().trim());
            } catch (RuntimeException exception) {
                throw new IllegalArgumentException("Enter a due date as YYYY-MM-DD for \""
                        + entry.book().title() + "\".");
            }
            if (dueDate.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Due date for \"" + entry.book().title()
                        + "\" must be today or later.");
            }
            result.add(new LendingService.LendRequest(entry.book().bookId(), dueDate));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Add at least one book to lend.");
        }
        return List.copyOf(result);
    }

    public PricingTerms pricingTerms() {
        return pricingForm.getTerms();
    }

    public void setPricingTerms(PricingTerms terms) {
        pricingForm.setTerms(terms);
    }

    public void clear() {
        books.clear();
        entries.removeAll();
        refreshCount();
        setStatus("Lend completed.");
    }

    public void setStatus(String message) {
        status.setText(message);
    }

    private void buildPanel() {
        setLayout(new BorderLayout(0, 8));
        setBackground(Theme.PAPER);
        setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        heading.setFont(Theme.BODY_BOLD);
        JPanel list = new JPanel(new BorderLayout(0, 5));
        list.setBackground(Theme.PAPER);
        list.add(heading, BorderLayout.NORTH);
        entries.setBackground(Theme.PAPER);
        entries.setLayout(new javax.swing.BoxLayout(entries, javax.swing.BoxLayout.Y_AXIS));
        list.add(new JScrollPane(entries), BorderLayout.CENTER);
        JPanel lower = new JPanel(new BorderLayout(0, 6));
        lower.setBackground(Theme.PAPER);
        preview.setForeground(Theme.INK);
        lower.add(preview, BorderLayout.NORTH);
        lower.add(pricingForm, BorderLayout.CENTER);
        JPanel actions = new JPanel(new BorderLayout(0, 5));
        actions.setOpaque(false);
        status.setForeground(Theme.MUTED);
        actions.add(status, BorderLayout.NORTH);
        JButton confirm = new JButton("Confirm lend");
        confirm.addActionListener(event -> confirmHandler.run());
        actions.add(confirm, BorderLayout.SOUTH);
        lower.add(actions, BorderLayout.SOUTH);
        add(list, BorderLayout.CENTER);
        add(lower, BorderLayout.SOUTH);
        updatePreview();
    }

    private void refreshCount() {
        heading.setText("Lend list (" + books.size() + ")");
        entries.revalidate();
        entries.repaint();
    }

    private void updatePreview() {
        if (!pricingForm.isPricingValid()) {
            preview.setText("Fix pricing values to preview base charges.");
            return;
        }
        try {
            java.math.BigDecimal base = pricingForm.getTerms().baseEnabled()
                    ? pricingForm.getTerms().baseAmount().multiply(java.math.BigDecimal.valueOf(books.size()))
                    : java.math.BigDecimal.ZERO;
            preview.setText("Base charges now: " + DisplayFormat.money(base)
                    + ". Rent and penalty are worked out when the book comes back.");
        } catch (IllegalArgumentException exception) {
            preview.setText("Fix pricing values to preview base charges.");
        }
    }

    private final class CartEntry {
        private final Book book;
        private final JTextField dueDate = new JTextField(10);
        private final JPanel panel = new JPanel(new BorderLayout(4, 3));

        private CartEntry(Book book, LocalDate due) {
            this.book = book;
            dueDate.setText(due.toString());
            panel.setBackground(Theme.WHITE);
            panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE),
                    BorderFactory.createEmptyBorder(5, 5, 5, 5)));
            JLabel title = new JLabel(book.title(), SwingConstants.LEADING);
            title.setToolTipText(book.title());
            panel.add(title, BorderLayout.NORTH);
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            controls.setOpaque(false);
            controls.add(new JLabel("Due"));
            controls.add(dueDate);
            JButton remove = new JButton("Remove");
            remove.addActionListener(event -> {
                books.remove(book.bookId());
                entries.remove(panel);
                refreshCount();
                updatePreview();
            });
            controls.add(remove);
            panel.add(controls, BorderLayout.CENTER);
        }

        private Book book() {
            return book;
        }

        private JTextField dueDate() {
            return dueDate;
        }

        private JPanel panel() {
            return panel;
        }
    }
}