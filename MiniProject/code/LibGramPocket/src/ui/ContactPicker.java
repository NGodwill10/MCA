package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import model.Contact;
import services.ContactSearchField;
import services.ContactService;
import services.DuplicateContactException;

public final class ContactPicker extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient ContactService contactService;
    private final JTextField search = new JTextField();
    private final DefaultListModel<Contact> resultsModel = new DefaultListModel<>();
    private final JList<Contact> results = new JList<>(resultsModel);
    private final JPanel cards = new JPanel(new java.awt.CardLayout());
    private final JPanel chooser = new JPanel(new BorderLayout(0, 8));
    private final JPanel form = new JPanel(new GridLayout(0, 2, 6, 6));
    private final JTextField name = new JTextField();
    private final JTextField phone = new JTextField();
    private final JTextField email = new JTextField();
    private final JTextArea notes = new JTextArea(3, 20);
    private final JLabel selectedSummary = new JLabel();
    private final Timer searchTimer = new Timer(250, event -> refreshResults());
    private transient Contact selectedContact;
    private transient Consumer<Contact> selectionChanged = contact -> {
    };

    public ContactPicker(ContactService contactService) {
        this.contactService = contactService;
        buildPanel();
        refreshResults();
    }

    public Optional<Contact> getSelectedContact() {
        return Optional.ofNullable(selectedContact);
    }

    public void setSelectionChanged(Consumer<Contact> listener) {
        selectionChanged = listener == null ? contact -> {
        } : listener;
    }

    private void buildPanel() {
        setLayout(new BorderLayout(0, Theme.GAP));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createTitledBorder("Choose a contact"));
        JPanel summary = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        summary.setBackground(Theme.WHITE);
        summary.add(selectedSummary);
        JButton change = linkButton("Change");
        change.addActionListener(event -> showChooser());
        summary.add(change);
        add(summary, BorderLayout.NORTH);

        chooser.setBackground(Theme.WHITE);
        chooser.add(search, BorderLayout.NORTH);
        results.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        results.setCellRenderer(new DefaultListCellRenderer() {
            private static final long serialVersionUID = 1L;

            @Override
            public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
                Contact contact = (Contact) value;
                label.setText(contact.name() + (contact.phone().isBlank() ? "" : "  |  " + contact.phone()));
                return label;
            }
        });
        results.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && results.getSelectedValue() != null) {
                setSelected(results.getSelectedValue());
            }
        });
        JPanel listArea = new JPanel(new BorderLayout(0, 5));
        listArea.setBackground(Theme.WHITE);
        listArea.add(new JScrollPane(results), BorderLayout.CENTER);
        JButton addNew = linkButton("New contact");
        addNew.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        addNew.addActionListener(event -> showForm());
        listArea.add(addNew, BorderLayout.SOUTH);
        chooser.add(listArea, BorderLayout.CENTER);
        cards.add(chooser, "chooser");

        form.setBackground(Theme.WHITE);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        form.add(new JLabel("Name *"));
        form.add(name);
        form.add(new JLabel("Phone"));
        form.add(phone);
        form.add(new JLabel("Email"));
        form.add(email);
        form.add(new JLabel("Notes"));
        form.add(new JScrollPane(notes));
        JPanel formActions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        formActions.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> showChooser());
        JButton save = new JButton("Add and select");
        save.addActionListener(event -> addContact());
        formActions.add(cancel);
        formActions.add(save);
        JPanel newContact = new JPanel(new BorderLayout(0, 8));
        newContact.setBackground(Theme.WHITE);
        newContact.add(form, BorderLayout.CENTER);
        newContact.add(formActions, BorderLayout.SOUTH);
        cards.add(newContact, "form");
        add(cards, BorderLayout.CENTER);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent event) {
                searchTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent event) {
                searchTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent event) {
                searchTimer.restart();
            }
        });
        ((java.awt.CardLayout) cards.getLayout()).show(cards, "chooser");
    }

    private void refreshResults() {
        String selectedContactId = selectedContact == null ? null : selectedContact.contactId();
        try {
            resultsModel.clear();
            contactService.search(ContactSearchField.ALL, search.getText())
                    .forEach(resultsModel::addElement);
            if (selectedContactId != null) {
                for (int index = 0; index < resultsModel.size(); index++) {
                    if (selectedContactId.equals(resultsModel.get(index).contactId())) {
                        results.setSelectedIndex(index);
                        results.ensureIndexIsVisible(index);
                        break;
                    }
                }
            }
        } catch (IOException exception) {
            System.getLogger(ContactPicker.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to search contacts", exception);
            JOptionPane.showMessageDialog(this, "Contacts could not be loaded.",
                    "Contacts", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showChooser() {
        selectedContact = null;
        selectedSummary.setText("No contact selected");
        ((java.awt.CardLayout) cards.getLayout()).show(cards, "chooser");
        selectionChanged.accept(null);
    }

    private void showForm() {
        name.setText("");
        phone.setText("");
        email.setText("");
        notes.setText("");
        ((java.awt.CardLayout) cards.getLayout()).show(cards, "form");
        name.requestFocusInWindow();
    }

    private void addContact() {
        Contact draft = new Contact("", name.getText(), phone.getText(), email.getText(), notes.getText(),
                LocalDateTime.now(), LocalDateTime.now(), "");
        try {
            selectNewContact(contactService.add(draft));
        } catch (DuplicateContactException exception) {
            int choice = JOptionPane.showConfirmDialog(this, exception.getMessage(), "Possible duplicate",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice == JOptionPane.YES_OPTION) {
                try {
                    selectNewContact(contactService.add(draft, true));
                } catch (IllegalArgumentException validationException) {
                    showValidation(validationException);
                } catch (IOException ioException) {
                    showStorageError(ioException);
                }
            }
        } catch (IllegalArgumentException exception) {
            showValidation(exception);
        } catch (IOException exception) {
            showStorageError(exception);
        }
    }

    private void selectNewContact(Contact contact) {
        setSelected(contact);
        refreshResults();
    }

    private void setSelected(Contact contact) {
        selectedContact = contact;
        selectedSummary.setText(contact.name() + (contact.phone().isBlank() ? "" : "  |  " + contact.phone()));
        ((java.awt.CardLayout) cards.getLayout()).show(cards, "chooser");
        selectionChanged.accept(contact);
    }

    private void showValidation(IllegalArgumentException exception) {
        JOptionPane.showMessageDialog(this, exception.getMessage(), "Contact", JOptionPane.ERROR_MESSAGE);
    }

    private void showStorageError(IOException exception) {
        System.getLogger(ContactPicker.class.getName()).log(System.Logger.Level.ERROR,
                "Unable to save contact", exception);
        JOptionPane.showMessageDialog(this, "The contact could not be saved. Check local storage and try again.",
                "Contacts", JOptionPane.ERROR_MESSAGE);
    }

    private static JButton linkButton(String label) {
        JButton button = new JButton(label);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setForeground(Theme.ACCENT_DARK);
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }
}
