package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.math.BigDecimal;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import model.Contact;

public final class ContactDetailPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel name = new JLabel();
    private final JLabel phone = new JLabel();
    private final JLabel email = new JLabel();
    private final JLabel created = new JLabel();
    private final JTextArea notes = new JTextArea(3, 24);
    private final ContactLentBooksPanel lentBooks = new ContactLentBooksPanel();
    private final ContactBorrowedBooksPanel borrowedBooks = new ContactBorrowedBooksPanel();
    private final ContactAmountsPanel amounts = new ContactAmountsPanel();

    public ContactDetailPanel(Runnable onBack) {
        setLayout(new BorderLayout(0, Theme.GAP));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JPanel heading = new JPanel(new BorderLayout());
        heading.setBackground(Theme.WHITE);
        JButton back = new JButton("Back to contacts");
        back.addActionListener(event -> onBack.run());
        heading.add(back, BorderLayout.WEST);
        add(heading, BorderLayout.NORTH);

        JPanel sections = new JPanel();
        sections.setBackground(Theme.WHITE);
        sections.setLayout(new javax.swing.BoxLayout(sections, javax.swing.BoxLayout.Y_AXIS));
        JPanel profile = new JPanel(new GridLayoutWithGap());
        profile.setBackground(Theme.WHITE);
        profile.setBorder(BorderFactory.createTitledBorder("Profile"));
        profile.add(name);
        profile.add(phone);
        profile.add(email);
        profile.add(created);
        notes.setEditable(false);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setOpaque(false);
        profile.add(new JScrollPane(notes));
        sections.add(profile);
        sections.add(lentBooks);
        sections.add(borrowedBooks);
        sections.add(amounts);
        add(new JScrollPane(sections), BorderLayout.CENTER);
    }

    public void setContact(Contact contact) {
        name.setText("Name: " + contact.name());
        phone.setText("Phone: " + display(contact.phone()));
        email.setText("Email: " + display(contact.email()));
        created.setText("Date added: " + DisplayFormat.dateTime(contact.createdAt()));
        notes.setText(contact.notes().isBlank() ? "No notes" : contact.notes());
    }

    public void setLoanData(List<List<String>> lends, List<List<String>> borrows,
            BigDecimal owedToOwner, BigDecimal owedToContact) {
        lentBooks.setData(lends);
        borrowedBooks.setData(borrows);
        amounts.setData(formatAmount(owedToOwner), formatAmount(owedToContact));
    }

    private static String formatAmount(BigDecimal amount) {
        return amount.signum() == 0 ? "" : DisplayFormat.money(amount);
    }

    private static String display(String value) {
        return value.isBlank() ? "Not provided" : value;
    }

    private static final class GridLayoutWithGap extends java.awt.GridLayout {
        private static final long serialVersionUID = 1L;

        private GridLayoutWithGap() {
            super(0, 1, 4, 4);
        }
    }
}