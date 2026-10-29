package ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
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
import model.Member;
import model.MemberStatus;
import model.MembershipType;
import model.User;
import services.DuplicateMemberEmailException;
import services.DuplicateMemberPhoneException;
import services.MemberService;
import services.SettingsDefaults;
import services.SettingsService;

public final class MemberFormDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final transient User actor;
    private final transient MemberService memberService;
    private final transient SettingsService settings;
    private final transient Member existing;
    private final transient Consumer<Member> onOpenExisting;
    private final JTextField memberId = new JTextField(26);
    private final JTextField name = new JTextField(26);
    private final JTextField phone = new JTextField(26);
    private final JTextField email = new JTextField(26);
    private final JTextArea address = new JTextArea(3, 26);
    private final JComboBox<MembershipType> membershipType = new JComboBox<>(MembershipType.values());
    private final JTextField joinDate = new JTextField(26);
    private final JTextField expiryDate = new JTextField(26);
    private final JComboBox<MemberStatus> status = new JComboBox<>(MemberStatus.values());
    private final transient Map<String, JLabel> fieldErrors = new LinkedHashMap<>();
    private final JLabel generalError = new JLabel(" ");
    private final JButton openExistingButton = new JButton("Open existing member");
    private transient Optional<Member> duplicateMember = Optional.empty();
    private transient Member savedMember;

    public MemberFormDialog(Window owner, User actor, MemberService memberService, SettingsService settings, Member existing,
            Consumer<Member> onOpenExisting) {
        super(owner, existing == null ? "Add member" : "Update member", Dialog.ModalityType.APPLICATION_MODAL);
        this.actor = actor;
        this.memberService = memberService;
        this.settings = settings;
        this.existing = existing;
        this.onOpenExisting = onOpenExisting;
        buildDialog();
    }

    public Optional<Member> showDialog() {
        setVisible(true);
        return Optional.ofNullable(savedMember);
    }

    private void buildDialog() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(Theme.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(4, 0, 4, 10);
        memberId.setEditable(false);
        memberId.setText(existing == null ? "Assigned on save" : existing.memberId());
        name.setText(existing == null ? "" : existing.name());
        phone.setText(existing == null ? "" : existing.phone());
        email.setText(existing == null ? "" : existing.email());
        address.setLineWrap(true);
        address.setWrapStyleWord(true);
        address.setText(existing == null ? "" : existing.address());
        membershipType.setSelectedItem(existing == null ? MembershipType.INDIVIDUAL : existing.membershipType());
        joinDate.setText(existing == null ? LocalDate.now().toString() : existing.joinDate().toString());
        expiryDate.setText(existing == null
                ? LocalDate.now().plusDays(settings.getInt(SettingsDefaults.key("membership.lengthDays.", MembershipType.INDIVIDUAL))).toString()
                : existing.expiryDate().toString());
        status.setSelectedItem(existing == null ? MemberStatus.ACTIVE : existing.status());
        addRow(form, constraints, "Member ID", memberId, "Member ID");
        addRow(form, constraints, "Name *", name, "Name");
        addRow(form, constraints, "Phone", phone, "Phone");
        addRow(form, constraints, "Email", email, "Email");
        addRow(form, constraints, "Address", new JScrollPane(address), "Address");
        addRow(form, constraints, "Membership type *", membershipType, "Membership type");
        addRow(form, constraints, "Join date (YYYY-MM-DD)", joinDate, "Join date");
        addRow(form, constraints, "Expiry date (YYYY-MM-DD)", expiryDate, "Expiry date");
        if (existing != null) {
            addRow(form, constraints, "Status", status, "Status");
        }
        membershipType.addActionListener(event -> updateDefaultExpiry());

        JPanel footer = new JPanel(new GridBagLayout());
        footer.setBackground(Theme.WHITE);
        GridBagConstraints footerConstraints = new GridBagConstraints();
        footerConstraints.gridx = 0;
        footerConstraints.gridy = 0;
        footerConstraints.gridwidth = 2;
        footerConstraints.anchor = GridBagConstraints.WEST;
        footerConstraints.insets = new Insets(3, 12, 3, 12);
        generalError.setForeground(Theme.ERROR);
        footer.add(generalError, footerConstraints);
        footerConstraints.gridy++;
        openExistingButton.setVisible(false);
        openExistingButton.addActionListener(event -> duplicateMember.ifPresent(member -> {
            onOpenExisting.accept(member);
            dispose();
        }));
        footer.add(openExistingButton, footerConstraints);
        footerConstraints.gridy++;
        footerConstraints.anchor = GridBagConstraints.EAST;
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> dispose());
        JButton save = new JButton(existing == null ? "Add member" : "Save changes");
        save.setBackground(Theme.ACCENT);
        save.setForeground(Theme.WHITE);
        save.addActionListener(event -> saveMember());
        name.addActionListener(event -> saveMember());
        buttons.add(cancel);
        buttons.add(save);
        footer.add(buttons, footerConstraints);

        add(new JScrollPane(form), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        getRootPane().registerKeyboardAction(event -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        pack();
        setSize(Math.max(getWidth(), 640), Math.min(Math.max(getHeight(), 680), 780));
        setLocationRelativeTo(getOwner());
    }

    private void saveMember() {
        clearErrors();
        try {
            persist(buildDraft(), false);
        } catch (DuplicateMemberEmailException exception) {
            duplicateMember = Optional.of(exception.existingMember());
            showFieldError("Email", exception.getMessage());
            openExistingButton.setVisible(true);
            pack();
        } catch (DuplicateMemberPhoneException exception) {
            int choice = javax.swing.JOptionPane.showConfirmDialog(this, exception.getMessage(),
                    "Duplicate phone number", javax.swing.JOptionPane.YES_NO_OPTION,
                    javax.swing.JOptionPane.QUESTION_MESSAGE);
            if (choice == javax.swing.JOptionPane.YES_OPTION) {
                try {
                    persist(buildDraft(), true);
                } catch (DuplicateMemberEmailException emailException) {
                    duplicateMember = Optional.of(emailException.existingMember());
                    showFieldError("Email", emailException.getMessage());
                    openExistingButton.setVisible(true);
                    pack();
                } catch (IllegalArgumentException | SecurityException validationException) {
                    showValidation(validationException);
                } catch (IOException ioException) {
                    showStorageError(ioException);
                }
            }
        } catch (IllegalArgumentException | SecurityException exception) {
            showValidation(exception);
        } catch (IOException exception) {
            showStorageError(exception);
        }
    }

    private void persist(Member draft, boolean confirmPhone) throws IOException {
        savedMember = existing == null ? memberService.add(actor, draft, confirmPhone)
                : memberService.update(actor, draft, confirmPhone);
        dispose();
    }

    private Member buildDraft() {
        LocalDate parsedJoinDate = parseDate(joinDate.getText(), "Join date");
        LocalDate parsedExpiryDate = parseDate(expiryDate.getText(), "Expiry date");
        return new Member(existing == null ? "" : existing.memberId(), name.getText(), phone.getText(), email.getText(),
                address.getText(), (MembershipType) membershipType.getSelectedItem(), parsedJoinDate,
                parsedExpiryDate, (MemberStatus) status.getSelectedItem(), existing == null ? null
                        : existing.createdAt(),
                existing == null ? null : existing.updatedAt());
    }

    private LocalDate parseDate(String value, String label) {
        try {
            return LocalDate.parse(value.trim());
        } catch (java.time.format.DateTimeParseException exception) {
            showFieldError(label, label + " must use YYYY-MM-DD format.");
            throw new IllegalArgumentException(label + " must use YYYY-MM-DD format.");
        }
    }

    private void updateDefaultExpiry() {
        if (existing != null) {
            return;
        }
        try {
            LocalDate join = LocalDate.parse(joinDate.getText().trim());
            MembershipType type = (MembershipType) membershipType.getSelectedItem();
            expiryDate.setText(join.plusDays(settings.getInt(SettingsDefaults.key("membership.lengthDays.", type))).toString());
        } catch (java.time.DateTimeException exception) {
            showFieldError("Join date", "Enter a valid join date before changing the membership type.");
        }
    }

    private void showValidation(RuntimeException exception) {
        String message = exception.getMessage();
        String field = message.startsWith("Name") ? "Name"
                : message.contains("phone or an email") || message.startsWith("Phone") ? "Phone"
                        : message.startsWith("Email") || message.startsWith("Enter a valid email") ? "Email"
                                : message.startsWith("Address") ? "Address"
                                        : message.startsWith("Membership type") ? "Membership type"
                                                : message.startsWith("Join date") ? "Join date"
                                                        : message.startsWith("Expiry date") ? "Expiry date"
                                                                : message.startsWith("Member status") ? "Status"
                                                                        : "Name";
        showFieldError(field, message);
    }

    private void showFieldError(String field, String message) {
        JLabel label = fieldErrors.get(field);
        if (label == null) {
            generalError.setText(message);
        } else {
            label.setText(message);
        }
    }

    private void clearErrors() {
        generalError.setText(" ");
        openExistingButton.setVisible(false);
        duplicateMember = Optional.empty();
        fieldErrors.values().forEach(label -> label.setText(" "));
    }

    private void showStorageError(IOException exception) {
        System.getLogger(MemberFormDialog.class.getName()).log(System.Logger.Level.ERROR,
                "Unable to save member", exception);
        generalError.setText("The member could not be saved. Check local storage and try again.");
    }

    private void addRow(JPanel panel, GridBagConstraints constraints, String label, java.awt.Component field,
            String errorKey) {
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setLabelFor(field);
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.weightx = 0;
        panel.add(fieldLabel, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        panel.add(field, constraints);
        JLabel error = new JLabel(" ");
        error.setForeground(Theme.ERROR);
        fieldErrors.put(errorKey, error);
        constraints.gridx = 1;
        constraints.gridy++;
        constraints.gridwidth = 1;
        panel.add(error, constraints);
    }
}
