package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import model.PricingTerms;
import model.PricingTerms.RentUnit;
import services.SettingsService;

public final class PricingForm extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("100000.00");
    private final transient SettingsService settings;
    private final JCheckBox baseEnabled = new JCheckBox("Base charge per book");
    private final JTextField baseAmount = amountField();
    private final JLabel baseError = errorLabel();
    private final JCheckBox rentEnabled = new JCheckBox("Rent");
    private final JTextField rentAmount = amountField();
    private final JLabel rentError = errorLabel();
    private final JCheckBox penaltyEnabled = new JCheckBox("Overdue penalty per day");
    private final JTextField penaltyAmount = amountField();
    private final JTextField penaltyCap = amountField();
    private final JLabel penaltyError = errorLabel();
    private final JLabel penaltyCapError = errorLabel();
    private final JLabel freeMessage = new JLabel("No charges selected: this will be free");
    private final JRadioButton day = new JRadioButton("Per day");
    private final JRadioButton week = new JRadioButton("Per week");
    private final JRadioButton month = new JRadioButton("Per month");
    private final transient List<JTextField> amountFields = new ArrayList<>();
    private transient Runnable validityChanged = () -> {
    };

    public PricingForm(SettingsService settings, PricingTerms initialTerms) {
        this.settings = settings;
        buildPanel();
        setTerms(initialTerms);
    }

    public PricingTerms getTerms() {
        if (!validateFields()) {
            throw new IllegalArgumentException("Correct the highlighted pricing values.");
        }
        return new PricingTerms(baseEnabled.isSelected(), new BigDecimal(baseAmount.getText().trim()),
                rentEnabled.isSelected(), selectedRentUnit(), new BigDecimal(rentAmount.getText().trim()),
                penaltyEnabled.isSelected(), new BigDecimal(penaltyAmount.getText().trim()),
                new BigDecimal(penaltyCap.getText().trim()));
    }

    public void setTerms(PricingTerms terms) {
        baseEnabled.setSelected(terms.baseEnabled());
        baseAmount.setText(terms.baseAmount().toPlainString());
        rentEnabled.setSelected(terms.rentEnabled());
        rentAmount.setText(terms.rentAmount().toPlainString());
        penaltyEnabled.setSelected(terms.penaltyEnabled());
        penaltyAmount.setText(terms.penaltyPerDay().toPlainString());
        penaltyCap.setText(terms.penaltyMaxPerBook().toPlainString());
        switch (terms.rentUnit()) {
            case DAY -> day.setSelected(true);
            case WEEK -> week.setSelected(true);
            case MONTH -> month.setSelected(true);
        }
        updateState();
    }

    public boolean isPricingValid() {
        return validateFields();
    }

    public void setValidityChanged(Runnable listener) {
        validityChanged = listener == null ? () -> {
        } : listener;
    }

    public void resetToDefaults() {
        setTerms(settings.pricingDefaults());
    }

    private void buildPanel() {
        setLayout(new GridBagLayout());
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createTitledBorder("Pricing"));
        ButtonGroup rentUnits = new ButtonGroup();
        rentUnits.add(day);
        rentUnits.add(week);
        rentUnits.add(month);
        JPanel units = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        units.setOpaque(false);
        units.add(day);
        units.add(week);
        units.add(month);

        int row = 0;
        addRow(row, baseEnabled, "Rs.", baseAmount, baseError);
        addRow(row += 2, rentEnabled, "Rs.", rentAmount, rentError);
        addRow(row += 2, new JLabel("Rent unit"), "", units, new JLabel());
        addRow(row += 2, penaltyEnabled, "Rs. per day", penaltyAmount, penaltyError);
        addRow(row += 2, new JLabel("Maximum penalty per book (0 = no cap)"), "Rs.", penaltyCap,
                penaltyCapError);
        row += 2;
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 0;
        filler.gridy = row++;
        filler.gridwidth = 4;
        filler.weightx = 1;
        filler.weighty = 1;
        filler.fill = GridBagConstraints.BOTH;
        add(new JPanel(), filler);

        freeMessage.setForeground(Theme.MUTED);
        GridBagConstraints message = constraints(0, row);
        message.gridwidth = 3;
        add(freeMessage, message);
        JButton reset = new JButton("Reset to defaults");
        reset.setBorderPainted(false);
        reset.setContentAreaFilled(false);
        reset.setForeground(Theme.ACCENT_DARK);
        reset.addActionListener(event -> resetToDefaults());
        GridBagConstraints resetConstraints = constraints(3, row);
        resetConstraints.anchor = GridBagConstraints.LINE_END;
        add(reset, resetConstraints);

        baseEnabled.addActionListener(event -> updateState());
        rentEnabled.addActionListener(event -> updateState());
        penaltyEnabled.addActionListener(event -> updateState());
        day.addActionListener(event -> updateState());
        week.addActionListener(event -> updateState());
        month.addActionListener(event -> updateState());
        for (JTextField field : amountFields) {
            field.getDocument().addDocumentListener(new DocumentListener() {
                @Override
                public void insertUpdate(DocumentEvent event) {
                    updateState();
                }

                @Override
                public void removeUpdate(DocumentEvent event) {
                    updateState();
                }

                @Override
                public void changedUpdate(DocumentEvent event) {
                    updateState();
                }
            });
        }
    }

    private void addRow(int row, java.awt.Component label, String prefix, java.awt.Component field,
            JLabel error) {
        GridBagConstraints labelConstraints = constraints(0, row);
        labelConstraints.gridwidth = 2;
        labelConstraints.weightx = 1;
        labelConstraints.fill = GridBagConstraints.HORIZONTAL;
        add(label, labelConstraints);
        JLabel unitLabel = new JLabel(prefix);
        GridBagConstraints prefixConstraints = constraints(2, row);
        add(unitLabel, prefixConstraints);
        GridBagConstraints fieldConstraints = constraints(3, row);
        fieldConstraints.weightx = 0.3;
        fieldConstraints.fill = GridBagConstraints.HORIZONTAL;
        if (field instanceof JTextField textField) {
            textField.setColumns(8);
            amountFields.add(textField);
        }
        add(field, fieldConstraints);
        GridBagConstraints errorConstraints = constraints(0, row + 1);
        errorConstraints.gridwidth = 4;
        errorConstraints.insets = new Insets(0, 18, 4, 4);
        add(error, errorConstraints);
    }

    private boolean validateFields() {
        boolean valid = validateAmount(baseAmount, baseEnabled.isSelected(), baseError, "Base charge")
                & validateAmount(rentAmount, rentEnabled.isSelected(), rentError, "Rent")
                & validateAmount(penaltyAmount, penaltyEnabled.isSelected(), penaltyError, "Overdue penalty")
                & validateAmount(penaltyCap, false, penaltyCapError, "Maximum penalty");
        return valid;
    }

    private boolean validateAmount(JTextField field, boolean enabled, JLabel error, String label) {
        String value = field.getText().trim();
        String issue = "";
        if (!value.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            issue = "Enter a number with at most 2 decimals.";
        } else {
            BigDecimal amount = new BigDecimal(value);
            if (amount.compareTo(MAX_AMOUNT) > 0) {
                issue = "Amount must be Rs. 100000.00 or less.";
            } else if (enabled && amount.signum() == 0) {
                issue = label + " must be greater than zero when enabled.";
            }
        }
        if (!issue.isEmpty()) {
            error.setText(issue);
            error.setVisible(true);
            return false;
        }
        error.setText(" ");
        error.setVisible(false);
        return true;
    }

    private void updateState() {
        baseAmount.setEnabled(baseEnabled.isSelected());
        rentAmount.setEnabled(rentEnabled.isSelected());
        day.setEnabled(rentEnabled.isSelected());
        week.setEnabled(rentEnabled.isSelected());
        month.setEnabled(rentEnabled.isSelected());
        penaltyAmount.setEnabled(penaltyEnabled.isSelected());
        penaltyCap.setEnabled(penaltyEnabled.isSelected());
        freeMessage.setVisible(!baseEnabled.isSelected() && !rentEnabled.isSelected() && !penaltyEnabled.isSelected());
        validateFields();
        validityChanged.run();
    }

    private RentUnit selectedRentUnit() {
        if (week.isSelected()) {
            return RentUnit.WEEK;
        }
        if (month.isSelected()) {
            return RentUnit.MONTH;
        }
        return RentUnit.DAY;
    }

    private static JTextField amountField() {
        return new JTextField("0.00");
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
        constraints.insets = new Insets(4, 6, 4, 6);
        constraints.anchor = GridBagConstraints.LINE_START;
        return constraints;
    }
}