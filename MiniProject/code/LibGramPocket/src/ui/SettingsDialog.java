package ui;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import model.PricingTerms;
import services.SettingsDefaults;
import services.SettingsService;

public final class SettingsDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private final transient SettingsService settings;
    private final PricingForm pricingForm;
    private final JTextField loanDays = new JTextField(8);
    private final JTextField dueSoonDays = new JTextField(8);
    private final JLabel loanError = errorLabel();
    private final JLabel dueSoonError = errorLabel();
    private final JCheckBox alertsEnabled = new JCheckBox("Show overdue and due-soon alerts");
    private final JButton save = new JButton("Save");

    public SettingsDialog(Window owner, SettingsService settings) {
        super(owner, "Settings", Dialog.ModalityType.APPLICATION_MODAL);
        this.settings = settings;
        pricingForm = new PricingForm(settings, settings.pricingDefaults());
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(620, 600));
        setSize(720, 700);
        add(buildContent(), BorderLayout.CENTER);
        add(buildActions(), BorderLayout.SOUTH);
        loanDays.setText(Integer.toString(settings.getInt("loan.defaultDays")));
        dueSoonDays.setText(Integer.toString(settings.getInt("alerts.dueSoonDays")));
        alertsEnabled.setSelected(settings.getBoolean("alerts.enabled"));
        addValidationListeners(loanDays);
        addValidationListeners(dueSoonDays);
        alertsEnabled.addActionListener(event -> updateSaveState());
        pricingForm.setValidityChanged(this::updateSaveState);
        getRootPane().registerKeyboardAction(event -> requestClose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        updateSaveState();
        setLocationRelativeTo(owner);
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(Theme.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(8, 12, 4, 12));
        GridBagConstraints pricing = constraints(0, 0);
        pricing.weightx = 1;
        pricing.weighty = 1;
        pricing.fill = GridBagConstraints.BOTH;
        content.add(pricingForm, pricing);

        JPanel otherSettings = new JPanel(new GridBagLayout());
        otherSettings.setBackground(Theme.WHITE);
        otherSettings.setBorder(BorderFactory.createTitledBorder("Loans and alerts"));
        addSetting(otherSettings, 0, new JLabel("Default loan period (days)"), loanDays, loanError);
        addSetting(otherSettings, 1, alertsEnabled, new JLabel(), new JLabel());
        addSetting(otherSettings, 2, new JLabel("Due soon window (days)"), dueSoonDays, dueSoonError);
        GridBagConstraints other = constraints(0, 1);
        other.weightx = 1;
        other.fill = GridBagConstraints.HORIZONTAL;
        content.add(otherSettings, other);
        return content;
    }

    private JPanel buildActions() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.setOpaque(false);
        JButton restore = new JButton("Restore defaults");
        restore.addActionListener(event -> restoreDefaults());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(event -> requestClose());
        save.addActionListener(event -> saveSettings());
        actions.add(restore);
        actions.add(cancel);
        actions.add(save);
        return actions;
    }

    private void restoreDefaults() {
        int choice = JOptionPane.showConfirmDialog(this, "Restore the default pricing and alert settings?",
                "Restore defaults", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        Map<String, String> defaults = SettingsDefaults.values();
        pricingForm.setTerms(defaultPricing(defaults));
        loanDays.setText(defaults.get("loan.defaultDays"));
        dueSoonDays.setText(defaults.get("alerts.dueSoonDays"));
        alertsEnabled.setSelected(Boolean.parseBoolean(defaults.get("alerts.enabled")));
        updateSaveState();
    }

    private void saveSettings() {
        if (!updateSaveState()) {
            return;
        }
        try {
            PricingTerms terms = pricingForm.getTerms();
            Map<String, String> changes = new LinkedHashMap<>();
            changes.put("pricing.base.enabled", Boolean.toString(terms.baseEnabled()));
            changes.put("pricing.base.amount", terms.baseAmount().toPlainString());
            changes.put("pricing.rent.enabled", Boolean.toString(terms.rentEnabled()));
            changes.put("pricing.rent.unit", terms.rentUnit().name());
            changes.put("pricing.rent.amount", terms.rentAmount().toPlainString());
            changes.put("pricing.penalty.enabled", Boolean.toString(terms.penaltyEnabled()));
            changes.put("pricing.penalty.perDay", terms.penaltyPerDay().toPlainString());
            changes.put("pricing.penalty.maxPerBook", terms.penaltyMaxPerBook().toPlainString());
            changes.put("loan.defaultDays", loanDays.getText().trim());
            changes.put("alerts.enabled", Boolean.toString(alertsEnabled.isSelected()));
            changes.put("alerts.dueSoonDays", dueSoonDays.getText().trim());
            settings.save(changes);
            dispose();
            JOptionPane.showMessageDialog(getOwner(), "Settings saved", "Settings",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Settings", JOptionPane.ERROR_MESSAGE);
        } catch (IOException exception) {
            System.getLogger(SettingsDialog.class.getName()).log(System.Logger.Level.ERROR,
                    "Unable to save settings", exception);
            JOptionPane.showMessageDialog(this, "Settings could not be saved. Check local storage and try again.",
                    "Settings", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void requestClose() {
        if (hasChanges()) {
            int choice = JOptionPane.showConfirmDialog(this, "Discard changes?", "Unsaved settings",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        dispose();
    }

    private boolean hasChanges() {
        if (!pricingForm.isPricingValid()) {
            return true;
        }
        PricingTerms terms = pricingForm.getTerms();
        return !Boolean.toString(terms.baseEnabled()).equals(settings.getString("pricing.base.enabled"))
                || !terms.baseAmount().toPlainString().equals(settings.getMoney("pricing.base.amount").toPlainString())
                || !Boolean.toString(terms.rentEnabled()).equals(settings.getString("pricing.rent.enabled"))
                || !terms.rentUnit().name().equals(settings.getString("pricing.rent.unit"))
                || !terms.rentAmount().toPlainString().equals(settings.getMoney("pricing.rent.amount").toPlainString())
                || !Boolean.toString(terms.penaltyEnabled()).equals(settings.getString("pricing.penalty.enabled"))
                || !terms.penaltyPerDay().toPlainString()
                        .equals(settings.getMoney("pricing.penalty.perDay").toPlainString())
                || !terms.penaltyMaxPerBook().toPlainString()
                        .equals(settings.getMoney("pricing.penalty.maxPerBook").toPlainString())
                || !loanDays.getText().trim().equals(settings.getString("loan.defaultDays"))
                || !Boolean.toString(alertsEnabled.isSelected()).equals(settings.getString("alerts.enabled"))
                || !dueSoonDays.getText().trim().equals(settings.getString("alerts.dueSoonDays"));
    }

    private boolean updateSaveState() {
        boolean loanValid = isIntegerInRange(loanDays.getText(), 1, 365);
        boolean dueSoonValid = isIntegerInRange(dueSoonDays.getText(), 1, 30);
        loanError.setText(loanValid ? " " : "Enter a whole number from 1 to 365.");
        dueSoonError.setText(dueSoonValid ? " " : "Enter a whole number from 1 to 30.");
        loanError.setVisible(!loanValid);
        dueSoonError.setVisible(!dueSoonValid);
        boolean valid = loanValid && dueSoonValid && pricingForm.isPricingValid();
        save.setEnabled(valid);
        return valid;
    }

    private static PricingTerms defaultPricing(Map<String, String> defaults) {
        return new PricingTerms(Boolean.parseBoolean(defaults.get("pricing.base.enabled")),
                new java.math.BigDecimal(defaults.get("pricing.base.amount")),
                Boolean.parseBoolean(defaults.get("pricing.rent.enabled")),
                PricingTerms.RentUnit.valueOf(defaults.get("pricing.rent.unit")),
                new java.math.BigDecimal(defaults.get("pricing.rent.amount")),
                Boolean.parseBoolean(defaults.get("pricing.penalty.enabled")),
                new java.math.BigDecimal(defaults.get("pricing.penalty.perDay")),
                new java.math.BigDecimal(defaults.get("pricing.penalty.maxPerBook")));
    }

    private static void addSetting(JPanel panel, int row, java.awt.Component label, java.awt.Component field,
            JLabel error) {
        GridBagConstraints labelConstraints = constraints(0, row * 2);
        labelConstraints.weightx = 1;
        labelConstraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(label, labelConstraints);
        GridBagConstraints fieldConstraints = constraints(1, row * 2);
        panel.add(field, fieldConstraints);
        GridBagConstraints errorConstraints = constraints(0, row * 2 + 1);
        errorConstraints.gridwidth = 2;
        errorConstraints.insets = new Insets(0, 6, 4, 6);
        panel.add(error, errorConstraints);
    }

    private void addValidationListeners(JTextField field) {
        field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                updateSaveState();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                updateSaveState();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                updateSaveState();
            }
        });
    }

    private static boolean isIntegerInRange(String value, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= minimum && parsed <= maximum;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static JLabel errorLabel() {
        JLabel label = new JLabel(" ");
        label.setForeground(Theme.ERROR);
        label.setVisible(false);
        return label;
    }

    private static GridBagConstraints constraints(int x, int y) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = x;
        constraints.gridy = y;
        constraints.insets = new Insets(5, 6, 5, 6);
        constraints.anchor = GridBagConstraints.LINE_START;
        return constraints;
    }
}