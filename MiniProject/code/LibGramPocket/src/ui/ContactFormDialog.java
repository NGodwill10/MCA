package ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import model.Contact;
import services.ContactService;
import services.DuplicateContactException;

public final class ContactFormDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final transient ContactService contactService;
    private final transient Contact existing;
    private final JTextField name = new JTextField(28);
    private final JTextField phone = new JTextField(28);
    private final JTextField email = new JTextField(28);
    private final JTextArea notes = new JTextArea(4, 28);
    private final JLabel error = new JLabel(" ");
    private transient Contact savedContact;

    public ContactFormDialog(Window owner, ContactService contactService, Contact existing) {
        super(owner, existing == null ? "Add contact" : "Update contact", Dialog.ModalityType.APPLICATION_MODAL);
        this.contactService = contactService;
        this.existing = existing;
        buildDialog();
    }

    public Optional<Contact> showDialog() {
        setVisible(true);
        return Optional.ofNullable(savedContact);
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
        constraints.insets = new Insets(6, 0, 6, 12);
        name.setText(existing == null ? "" : existing.name());
        phone.setText(existing == null ? "" : existing.phone());
        email.setText(existing == null ? "" : existing.email());
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);
        notes.setText(existing == null ? "" : existing.notes());
        addRow(form, constraints, "Name *", name);
        addRow(form, constraints, "Phone", phone);
        addRow(form, constraints, "Email", email);
        addRow(form, constraints, "Notes", new JScrollPane(notes));

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.setBackground(Theme.WHITE);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));
        error.setForeground(Theme.ERROR);
        footer.add(error, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton save = new JButton(existing == null ? "Add contact" : "Save changes");
        save.addActionListener(event -> saveContact());
        name.addActionListener(event -> saveContact());
        buttons.add(cancel);
        buttons.add(save);
        footer.add(buttons, BorderLayout.SOUTH);

        add(form, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void saveContact() {
        error.setText(" ");
        Contact draft = draft();
        try {
            savedContact = existing == null ? contactService.add(draft) : contactService.update(draft);
            dispose();
        } catch (DuplicateContactException exception) {
            int choice = javax.swing.JOptionPane.showConfirmDialog(this, exception.getMessage(),
                    "Possible duplicate", javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.QUESTION_MESSAGE);
            if (choice == javax.swing.JOptionPane.YES_OPTION) {
                saveConfirmedDuplicate(draft);
            }
        } catch (IllegalArgumentException exception) {
            error.setText(exception.getMessage());
        } catch (IOException exception) {
            System.getLogger(ContactFormDialog.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to save contact", exception);
            error.setText("The contact could not be saved. Check local storage and try again.");
        }
    }

    private void saveConfirmedDuplicate(Contact draft) {
        try {
            if (existing == null) {
                savedContact = contactService.add(draft, true);
            } else {
                savedContact = contactService.update(draft, true);
            }
            dispose();
        } catch (IllegalArgumentException exception) {
            error.setText(exception.getMessage());
        } catch (IOException exception) {
            System.getLogger(ContactFormDialog.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to save contact", exception);
            error.setText("The contact could not be saved. Check local storage and try again.");
        }
    }

    private Contact draft() {
        return new Contact(existing == null ? "" : existing.contactId(), name.getText(), phone.getText(),
                email.getText(), notes.getText(), existing == null ? null : existing.createdAt(),
                existing == null ? null : existing.updatedAt(), existing == null ? "" : existing.onlineUserId());
    }

    private static void addRow(JPanel panel, GridBagConstraints constraints, String label, java.awt.Component field) {
        JLabel text = new JLabel(label);
        text.setLabelFor(field);
        constraints.gridx = 0;
        constraints.weightx = 0;
        panel.add(text, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(field, constraints);
        constraints.gridy++;
    }
}