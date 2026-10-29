package ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ChangeListener;
import model.MembershipType;
import model.User;
import services.SettingsDefaults;
import services.SettingsService;
import services.AccountService;
import services.AlertScanner;

/** Modal editor for the Phase 4 settings. */
public final class SettingsDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static int lastTabIndex;
    private final transient User actor;
    private final transient SettingsService settings;
    private final transient AccountService accounts;
    private final transient AlertScanner alertScanner;
    private final transient Map<String, JTextField> fields = new LinkedHashMap<>();
    private final transient Map<String, JCheckBox> checks = new LinkedHashMap<>();
    private final transient Map<String, java.awt.Component> chargeFields = new LinkedHashMap<>();
    private final JLabel error = new JLabel(" ");
    private final JButton save = new JButton("Save");
    private final JTabbedPane tabs = new JTabbedPane();
    private transient Map<String, String> initial;

    public SettingsDialog(Window owner, User actor, SettingsService settings, AccountService accounts) {
        this(owner, actor, settings, accounts, null);
    }

    public SettingsDialog(Window owner, User actor, SettingsService settings, AccountService accounts,
            AlertScanner alertScanner) {
        super(owner, "Settings", Dialog.ModalityType.APPLICATION_MODAL);
        this.actor = actor;
        this.settings = settings;
        this.accounts = accounts;
        this.alertScanner = alertScanner;
        initial = new LinkedHashMap<>(settings.snapshot());
        build();
    }

    private void build() {
        tabs.addTab("Lending charges", scroll(chargesTab()));
        tabs.addTab("Loans and limits", scroll(loansTab()));
        tabs.addTab("Fees", scroll(feesTab()));
        tabs.addTab("Membership", scroll(membershipTab()));
        tabs.addTab("Alerts", scroll(alertsTab()));
        tabs.addTab("Accounts", accountsTab());
        tabs.setSelectedIndex(Math.min(lastTabIndex, tabs.getTabCount() - 1));
        tabs.addChangeListener((ChangeListener) event -> lastTabIndex = tabs.getSelectedIndex());
        add(tabs, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        error.setForeground(Theme.ERROR);
        footer.add(error, BorderLayout.CENTER);
        JPanel buttons = new JPanel();
        JButton restore = new JButton("Restore defaults");
        restore.addActionListener(event -> restoreDefaults());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> cancel());
        save.setEnabled(false);
        save.addActionListener(event -> save());
        buttons.add(restore);
        buttons.add(cancel);
        buttons.add(save);
        footer.add(buttons, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        pack();
        setSize(Math.max(getWidth(), 760), Math.min(Math.max(getHeight(), 520), 760));
        setLocationRelativeTo(getOwner());
        refreshValidity();
    }

    private JPanel chargesTab() {
        JPanel panel = formPanel();
        addCharge(panel, "charge.base", "Base charge per book", "charge.base.amount");
        addCharge(panel, "charge.rent", "Rent", "charge.rent.amount");
        JPanel units = new JPanel();
        units.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        for (String unit : new String[] { "DAY", "WEEK", "MONTH" }) {
            JRadioButton radio = new JRadioButton("per " + unit.toLowerCase(),
                    unit.equals(settings.getString("charge.rent.unit")));
            radio.setActionCommand(unit);
            radio.addActionListener(event -> changed());
            group.add(radio);
            units.add(radio);
        }
        addRow(panel, "Rent unit", units);
        addCharge(panel, "charge.penalty", "Overdue fine per day", "charge.penalty.perDay");
        addField(panel, "charge.penalty.maxPerItem", "Maximum fine per item");
        chargeFields.put("charge.penalty.enabled", fields.get("charge.penalty.maxPerItem"));
        return panel;
    }

    private JPanel loansTab() {
        JPanel panel = formPanel();
        addField(panel, "loan.defaultDays", "Default loan period (days)");
        addField(panel, "loan.maxDays", "Maximum loan period (days)");
        for (MembershipType type : MembershipType.values()) {
            addField(panel, SettingsDefaults.key("limit.", type), "Borrowing limit - " + label(type));
        }
        addField(panel, "lost.afterDays", "Treat as lost after days overdue");
        addField(panel, "refund.windowDays", "Refund window (days)");
        addField(panel, "stock.lowThreshold", "Low-stock threshold");
        return panel;
    }

    private JPanel feesTab() {
        JPanel panel = formPanel();
        addField(panel, "fee.lostProcessing", "Lost-book processing fee");
        addField(panel, "fee.damagedProcessing", "Damaged-book processing fee");
        addField(panel, "fee.cardReplacement", "Card replacement fee");
        addField(panel, "fee.reservation", "Reservation fee");
        addField(panel, "reservation.holdDays", "Reservation hold days");
        addField(panel, "reservation.maxPerMember", "Maximum reservations per member");
        return panel;
    }

    private JPanel membershipTab() {
        JPanel panel = formPanel();
        for (MembershipType type : MembershipType.values()) {
            addField(panel, SettingsDefaults.key("membership.label.", type), type.name() + " display label");
            addField(panel, SettingsDefaults.key("membership.lengthDays.", type), type.name() + " length (days)");
            addField(panel, SettingsDefaults.key("fee.membership.registration.", type), type.name() + " registration fee");
            addField(panel, SettingsDefaults.key("fee.membership.annual.", type), type.name() + " annual fee");
        }
        return panel;
    }

    private JPanel alertsTab() {
        JPanel panel = formPanel();
        JCheckBox enabled = new JCheckBox("Enable automatic alerts", settings.getBoolean("alerts.enabled"));
        checks.put("alerts.enabled", enabled);
        enabled.addActionListener(event -> changed());
        addRow(panel, "Alerts", enabled);
        addField(panel, "alerts.membershipExpiryDays", "Membership expiry warning (days)");
        addRow(panel, "Related settings", new JLabel("Low-stock threshold and lost-after days are on Loans and limits."));
        JButton scan = new JButton("Scan now");
        scan.addActionListener(event -> { if (alertScanner != null) alertScanner.scanAsync(); });
        addRow(panel, "", scan);
        return panel;
    }

    private JPanel accountsTab() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(Theme.WHITE);
        DefaultTableModel model = new DefaultTableModel(
                new String[] { "Username", "Display name", "Role", "Active", "ID" }, 0) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getColumnModel().getColumn(4).setMinWidth(0);
        table.getColumnModel().getColumn(4).setMaxWidth(0);
        refreshAccountRows(model);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel editor = new JPanel(new java.awt.GridLayout(2, 4, 6, 6));
        editor.setBackground(Theme.WHITE);
        JTextField username = new JTextField();
        JTextField displayName = new JTextField();
        JTextField password = new JTextField();
        JButton create = new JButton("Create staff");
        editor.add(new JLabel("Username")); editor.add(username);
        editor.add(new JLabel("Display name")); editor.add(displayName);
        editor.add(new JLabel("Temporary password")); editor.add(password);
        editor.add(create);
        JButton reset = new JButton("Reset password");
        JButton toggle = new JButton("Enable/disable selected");
        JPanel actions = new JPanel(); actions.setOpaque(false);
        actions.add(reset); actions.add(toggle);
        JPanel south = new JPanel(new BorderLayout()); south.setOpaque(false);
        south.add(editor, BorderLayout.CENTER); south.add(actions, BorderLayout.SOUTH);
        panel.add(south, BorderLayout.SOUTH);
        create.addActionListener(event -> {
            try {
                accounts.createStaff(actor, username.getText(), displayName.getText(), password.getText().toCharArray());
                refreshAccountRows(model); username.setText(""); displayName.setText(""); password.setText("");
            } catch (Exception exception) { showAccountError(exception); }
        });
        reset.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) { showAccountError(new IllegalArgumentException("Select an account first.")); return; }
            try {
                accounts.resetPassword(actor, (String) model.getValueAt(row, 4), password.getText().toCharArray());
                password.setText("");
            } catch (Exception exception) { showAccountError(exception); }
        });
        toggle.addActionListener(event -> {
            int row = table.getSelectedRow();
            if (row < 0) { showAccountError(new IllegalArgumentException("Select an account first.")); return; }
            try {
                boolean active = (Boolean) model.getValueAt(row, 3);
                accounts.setActive(actor, (String) model.getValueAt(row, 4), !active);
                refreshAccountRows(model);
            } catch (Exception exception) { showAccountError(exception); }
        });
        return panel;
    }

    private void refreshAccountRows(DefaultTableModel model) {
        model.setRowCount(0);
        accounts.list(actor).forEach(user -> model.addRow(new Object[] {
                user.username(), user.displayName(), user.role().name(), user.active(), user.userId() }));
    }

    private void showAccountError(Exception exception) {
        javax.swing.JOptionPane.showMessageDialog(this, exception.getMessage(), "Account error",
                javax.swing.JOptionPane.ERROR_MESSAGE);
    }

    private void addCharge(JPanel panel, String prefix, String title, String amountKey) {
        JCheckBox check = new JCheckBox(title, settings.getBoolean(prefix + ".enabled"));
        checks.put(prefix + ".enabled", check);
        check.addActionListener(event -> changed());
        addRow(panel, title, check);
        addField(panel, amountKey, "  Amount (Rs.)");
        chargeFields.put(prefix + ".enabled", fields.get(amountKey));
    }

    private void addField(JPanel panel, String key, String title) {
        JTextField field = new JTextField(settings.getString(key), 14);
        fields.put(key, field);
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { changed(); }
            public void removeUpdate(DocumentEvent event) { changed(); }
            public void changedUpdate(DocumentEvent event) { changed(); }
        });
        addRow(panel, title, field);
    }

    private void addRow(JPanel panel, String title, java.awt.Component value) {
        GridBagConstraints left = constraints(0, panel.getComponentCount() / 2);
        GridBagConstraints right = constraints(1, left.gridy);
        panel.add(new JLabel(title), left);
        panel.add(value, right);
    }

    private JPanel formPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Theme.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
        return panel;
    }

    private JScrollPane scroll(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel);
        scroll.getViewport().setBackground(Theme.WHITE);
        return scroll;
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints result = new GridBagConstraints();
        result.gridx = x;
        result.gridy = y;
        result.weightx = x == 1 ? 1 : 0;
        result.fill = GridBagConstraints.HORIZONTAL;
        result.insets = new Insets(5, 5, 5, 5);
        return result;
    }

    private String label(MembershipType type) {
        return settings.getString(SettingsDefaults.key("membership.label.", type));
    }

    private Map<String, String> proposed() {
        Map<String, String> result = new LinkedHashMap<>(settings.snapshot());
        fields.forEach((key, field) -> result.put(key, field.getText().trim()));
        checks.forEach((key, check) -> result.put(key, Boolean.toString(check.isSelected())));
        JPanel charges = (JPanel) ((JScrollPane) tabs.getComponentAt(0)).getViewport().getView();
        for (java.awt.Component component : charges.getComponents()) {
            if (component instanceof JPanel unitPanel) {
                for (java.awt.Component child : unitPanel.getComponents()) {
                    if (child instanceof JRadioButton radio && radio.isSelected()) {
                        result.put("charge.rent.unit", radio.getActionCommand());
                    }
                }
            }
        }
        return result;
    }

    private void changed() {
        refreshValidity();
    }

    private void refreshValidity() {
        chargeFields.forEach((key, component) -> component.setEnabled(checks.get(key).isSelected()));
        try {
            Map<String, String> current = proposed();
            settings.validate(current);
            error.setText(" ");
            save.setEnabled(!current.equals(initial));
        } catch (RuntimeException exception) {
            error.setText(exception.getMessage());
            save.setEnabled(false);
        }
    }

    private void save() {
        try {
            settings.save(actor, proposed());
            dispose();
        } catch (Exception exception) {
            error.setText(exception.getMessage());
        }
    }

    private void restoreDefaults() {
        if (javax.swing.JOptionPane.showConfirmDialog(this, "Restore defaults for all settings?", "Restore defaults",
                javax.swing.JOptionPane.YES_NO_OPTION, javax.swing.JOptionPane.WARNING_MESSAGE)
                != javax.swing.JOptionPane.YES_OPTION) {
            return;
        }
        Map<String, String> defaults = SettingsDefaults.values();
        fields.forEach((key, field) -> field.setText(defaults.getOrDefault(key, field.getText())));
        checks.forEach((key, check) -> check.setSelected(Boolean.parseBoolean(defaults.get(key))));
        changed();
    }

    private void cancel() {
        if (!proposed().equals(initial) || javax.swing.JOptionPane.showConfirmDialog(this, "Discard changes?",
                "Cancel", javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION) {
            dispose();
        }
    }
}
