package ui;

import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

public final class ContactAmountsPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel status = new JLabel("No amounts recorded yet.");
    private final JLabel owedToOwner = new JLabel();
    private final JLabel owedToContact = new JLabel();

    public ContactAmountsPanel() {
        super(new GridLayout(0, 1, 4, 4));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createTitledBorder("Amounts"));
        status.setForeground(Theme.MUTED);
        add(status);
        add(owedToOwner);
        add(owedToContact);
        setData("", "");
    }

    public void setData(String contactOwesOwner, String ownerOwesContact) {
        boolean hasAmounts = !contactOwesOwner.isBlank() || !ownerOwesContact.isBlank();
        status.setVisible(!hasAmounts);
        owedToOwner.setVisible(hasAmounts);
        owedToContact.setVisible(hasAmounts);
        owedToOwner.setText("They owe you: " + (contactOwesOwner.isBlank() ? "Rs. 0.00" : contactOwesOwner));
        owedToContact.setText("You owe them: " + (ownerOwesContact.isBlank() ? "Rs. 0.00" : ownerOwesContact));
    }
}