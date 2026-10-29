package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.WindowConstants;
import model.Book;
import model.Contact;
import model.Loan;
import model.LoanDirection;
import model.ReturnCondition;
import services.LendingService;
import services.LoanRules;
import services.ContactService;
import services.BookService;

public final class ReceiveReturnDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final transient ContactService contacts;
    private final transient BookService books;
    private final transient LendingService lending;
    private final transient Runnable returned;
    private final LoanDirection direction;
    private final JComboBox<Contact> contactPicker = new JComboBox<>();
    private final JTextField returnDate = new JTextField(LocalDate.now().toString(), 10);
    private final JPanel returnItems = new JPanel();
    private final JTextArea bill = new JTextArea(6, 42);
    private final JLabel total = new JLabel("Total: Rs. 0.00");
    private final JLabel empty = new JLabel();
    private final JRadioButton paid = new JRadioButton("Mark as paid");
    private final JRadioButton owed = new JRadioButton("Leave as owed", true);
    private final JButton confirm = new JButton("Confirm return");
    private final transient List<ReturnItem> items = new ArrayList<>();
    private final String selectedLoanId;

    public ReceiveReturnDialog(java.awt.Window owner, ContactService contacts, BookService books,
            LendingService lending, Runnable returned, String selectedLoanId) {
        this(owner, contacts, books, lending, returned, selectedLoanId, LoanDirection.LENT);
    }

    public ReceiveReturnDialog(java.awt.Window owner, ContactService contacts, BookService books,
            LendingService lending, Runnable returned, String selectedLoanId, LoanDirection direction) {
        super(owner, direction == LoanDirection.BORROWED ? "Return to lender" : "Receive a return",
                ModalityType.APPLICATION_MODAL);
        this.contacts = contacts;
        this.books = books;
        this.lending = lending;
        this.returned = returned == null ? () -> {
        } : returned;
        this.selectedLoanId = selectedLoanId;
        this.direction = direction;
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(760, 680);
        setLayout(new BorderLayout(Theme.GAP, Theme.GAP));
        buildPanel();
        loadContacts();
        setLocationRelativeTo(owner);
    }

    private void buildPanel() {
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Contact"));
        contactPicker.setRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                return super.getListCellRendererComponent(list,
                        value instanceof Contact contact ? contact.name() : "Choose a contact", index,
                        isSelected, cellHasFocus);
            }
        });
        contactPicker.addActionListener(event -> loadLoans());
        top.add(contactPicker);
        JLabel returnDateLabel = new JLabel("Return date (YYYY-MM-DD)");
        returnDateLabel.setLabelFor(returnDate);
        top.add(returnDateLabel);
        top.add(returnDate);
        returnDate.addActionListener(event -> refreshBill());
        returnDate.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                refreshBill();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                refreshBill();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                refreshBill();
            }
        });
        add(top, BorderLayout.NORTH);

        returnItems.setLayout(new javax.swing.BoxLayout(returnItems, javax.swing.BoxLayout.Y_AXIS));
        returnItems.setBackground(Theme.WHITE);
        returnItems.add(empty);
        add(new JScrollPane(returnItems), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setBorder(BorderFactory.createTitledBorder("Itemized bill"));
        bill.setEditable(false);
        bill.setLineWrap(true);
        bill.setWrapStyleWord(true);
        bottom.add(new JScrollPane(bill), BorderLayout.CENTER);
        JPanel decisions = new JPanel(new BorderLayout());
        ButtonGroup payment = new ButtonGroup();
        payment.add(paid);
        payment.add(owed);
        JPanel choices = new JPanel(new FlowLayout(FlowLayout.LEFT));
        choices.add(paid);
        choices.add(owed);
        decisions.add(total, BorderLayout.WEST);
        decisions.add(choices, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        confirm.setEnabled(false);
        confirm.setText(direction == LoanDirection.BORROWED ? "Confirm return to lender" : "Confirm return");
        confirm.addActionListener(event -> confirmReturn());
        actions.add(cancel);
        actions.add(confirm);
        decisions.add(actions, BorderLayout.EAST);
        bottom.add(decisions, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);
    }

    private void loadContacts() {
        try {
            List<Contact> availableContacts = contacts.listSorted().stream()
                    .filter(contact -> hasActiveLoans(contact.contactId())).toList();
            availableContacts.forEach(contactPicker::addItem);
            if (selectedLoanId != null) {
                Loan selected = lending.findLoan(selectedLoanId).orElse(null);
                if (selected != null && selected.direction() == direction) {
                    availableContacts.stream().filter(contact -> contact.contactId().equals(selected.contactId()))
                            .findFirst().ifPresent(contactPicker::setSelectedItem);
                }
            }
            loadLoans();
        } catch (IOException exception) {
            showStorageError(exception);
        }
    }

    private boolean hasActiveLoans(String contactId) {
        try {
            return direction == LoanDirection.BORROWED
                    ? !lending.activeBorrowsByContact(contactId).isEmpty()
                    : !lending.activeLendsByContact(contactId).isEmpty();
        } catch (IOException exception) {
            showStorageError(exception);
            return false;
        }
    }

    private void loadLoans() {
        items.clear();
        returnItems.removeAll();
        Contact contact = (Contact) contactPicker.getSelectedItem();
        if (contact == null) {
            empty.setText(direction == LoanDirection.BORROWED
                    ? "Choose a lender with active borrows."
                    : "Choose a contact with active lends.");
            returnItems.add(empty);
            refreshBill();
            return;
        }
        try {
            List<Loan> active = direction == LoanDirection.BORROWED
                    ? lending.activeBorrowsByContact(contact.contactId())
                    : lending.activeLendsByContact(contact.contactId());
            for (Loan loan : active) {
                BigDecimal price = books.getById(loan.bookId()).map(Book::price).orElse(BigDecimal.ZERO);
                ReturnItem item = new ReturnItem(loan, price);
                items.add(item);
                returnItems.add(item.panel);
                if (loan.loanId().equals(selectedLoanId)) {
                    item.selected.setSelected(true);
                }
            }
            if (items.isEmpty()) {
                empty.setText(direction == LoanDirection.BORROWED
                        ? "No active borrows for this lender."
                        : "No active lends for this contact.");
                returnItems.add(empty);
            }
        } catch (IOException exception) {
            showStorageError(exception);
        }
        returnItems.revalidate();
        returnItems.repaint();
        refreshBill();
    }

    private void refreshBill() {
        StringBuilder summary = new StringBuilder();
        BigDecimal grandTotal = BigDecimal.ZERO;
        boolean anySelected = false;
        boolean valid = true;
        LocalDate date;
        try {
            date = LocalDate.parse(returnDate.getText().trim());
        } catch (RuntimeException exception) {
            bill.setText("Enter the return date as YYYY-MM-DD.");
            total.setText("Total: Rs. 0.00");
            confirm.setEnabled(false);
            return;
        }
        for (ReturnItem item : items) {
            if (!item.selected.isSelected()) {
                continue;
            }
            anySelected = true;
            try {
                ReturnCondition condition = direction == LoanDirection.BORROWED ? ReturnCondition.GOOD
                        : (ReturnCondition) item.condition.getSelectedItem();
                BigDecimal damage = direction == LoanDirection.BORROWED || condition == ReturnCondition.GOOD
                        ? BigDecimal.ZERO
                        : new BigDecimal(item.damageAmount.getText().trim());
                var itemBill = lending.previewReturn(item.loan, date, condition, damage);
                summary.append(item.loan.bookTitleSnapshot()).append(':').append('\n');
                itemBill.lines().forEach(line -> summary.append("  ").append(line.label()).append("  ")
                        .append(DisplayFormat.money(line.amount())).append('\n'));
                summary.append("  Total  ").append(DisplayFormat.money(itemBill.total())).append("\n\n");
                grandTotal = grandTotal.add(itemBill.total());
            } catch (RuntimeException exception) {
                summary.append(item.loan.bookTitleSnapshot()).append(": ").append(exception.getMessage())
                        .append("\n\n");
                valid = false;
            }
        }
        bill.setText(summary.isEmpty()
                ? direction == LoanDirection.BORROWED ? "Select one or more active borrows to calculate the bill."
                        : "Select one or more active lends to calculate the bill."
                : summary.toString().trim());
        total.setText("Total: " + DisplayFormat.money(grandTotal));
        confirm.setEnabled(anySelected && valid);
    }

    private void confirmReturn() {
        List<LendingService.ReturnRequest> requests = new ArrayList<>();
        try {
            for (ReturnItem item : items) {
                if (item.selected.isSelected()) {
                    LocalDate date = LocalDate.parse(returnDate.getText().trim());
                    ReturnCondition condition = direction == LoanDirection.BORROWED ? ReturnCondition.GOOD
                            : (ReturnCondition) item.condition.getSelectedItem();
                    BigDecimal damage = direction == LoanDirection.BORROWED || condition == ReturnCondition.GOOD
                            ? BigDecimal.ZERO
                            : new BigDecimal(item.damageAmount.getText().trim());
                    requests.add(new LendingService.ReturnRequest(item.loan.loanId(), date, condition,
                            damage));
                }
            }
            lending.returnLoans(requests, paid.isSelected());
            returned.run();
            dispose();
        } catch (NumberFormatException exception) {
            JOptionPane.showMessageDialog(this, "Enter a valid damage amount for each damaged book.", "Return",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Return", JOptionPane.ERROR_MESSAGE);
        } catch (IOException exception) {
            showStorageError(exception);
        }
    }

    private void showStorageError(IOException exception) {
        System.getLogger(ReceiveReturnDialog.class.getName()).log(System.Logger.Level.ERROR,
                "Unable to process a lend return", exception);
        JOptionPane.showMessageDialog(this, "The return could not be saved. Check local storage and try again.",
                "Return", JOptionPane.ERROR_MESSAGE);
    }

    private final class ReturnItem {
        private final Loan loan;
        private final JCheckBox selected = new JCheckBox();
        private final JComboBox<ReturnCondition> condition = new JComboBox<>(ReturnCondition.values());
        private final JTextField damageAmount = new JTextField("0.00", 8);
        private final JPanel panel = new JPanel(new GridLayout(0, 1, 3, 3));
        private ReturnCondition previousCondition = ReturnCondition.GOOD;

        private ReturnItem(Loan loan, BigDecimal price) {
            this.loan = loan;
            panel.setBackground(Theme.WHITE);
            panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.LINE),
                    BorderFactory.createEmptyBorder(7, 8, 7, 8)));
            JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            title.setOpaque(false);
            title.add(selected);
            title.add(new JLabel(loan.bookTitleSnapshot() + "  |  due " + loan.dueDate()));
            long overdueDays = LoanRules.daysOverdue(loan.dueDate(), LocalDate.now());
            JLabel overdueBadge = new JLabel(overdueDays == 0 ? "On time" : overdueDays + " days overdue");
            overdueBadge.setOpaque(true);
            overdueBadge.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
            overdueBadge.setForeground(overdueDays == 0 ? Theme.MUTED : Theme.WHITE);
            overdueBadge.setBackground(overdueDays == 0 ? Theme.PAPER : Theme.ERROR);
            title.add(overdueBadge);
            panel.add(title);
            JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            options.setOpaque(false);
            options.add(new JLabel("Condition"));
            condition.setRenderer(new DefaultListCellRenderer() {
                private static final long serialVersionUID = 1L;

                @Override
                public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> list, Object value,
                        int index, boolean isSelected, boolean cellHasFocus) {
                    String label = switch ((ReturnCondition) value) {
                        case GOOD -> "Good";
                        case MINOR_DAMAGE -> "Minor damage";
                        case SEVERE_DAMAGE -> "Severe damage";
                    };
                    return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
                }
            });
            options.add(condition);
            options.add(new JLabel("Damage Rs."));
            options.add(damageAmount);
            if (direction == LoanDirection.BORROWED) {
                options.getComponents()[0].setVisible(false);
                condition.setVisible(false);
                options.getComponents()[2].setVisible(false);
                damageAmount.setVisible(false);
            }
            panel.add(options);
            condition.addActionListener(event -> {
                ReturnCondition next = (ReturnCondition) condition.getSelectedItem();
                if (next == ReturnCondition.SEVERE_DAMAGE && previousCondition != ReturnCondition.SEVERE_DAMAGE) {
                    damageAmount.setText(price.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
                } else if (next == ReturnCondition.GOOD) {
                    damageAmount.setText("0.00");
                } else if (previousCondition == ReturnCondition.SEVERE_DAMAGE) {
                    damageAmount.setText("0.00");
                }
                previousCondition = next;
                damageAmount.setEnabled(next != ReturnCondition.GOOD);
                refreshBill();
            });
            selected.addActionListener(event -> refreshBill());
            damageAmount.setEnabled(false);
            damageAmount.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent event) {
                    refreshBill();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent event) {
                    refreshBill();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent event) {
                    refreshBill();
                }
            });
        }
    }
}