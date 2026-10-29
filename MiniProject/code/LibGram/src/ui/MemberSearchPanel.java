package ui;

import java.awt.BorderLayout;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import services.MemberSearchField;
import services.MemberStatusFilter;

public final class MemberSearchPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final String[] FIELDS = {
            "All", "Name", "Member ID", "Phone", "Email", "Membership type", "Status" };
    private static final String[] STATUSES = { "All", "Active", "Suspended", "Inactive", "Expired" };

    private final JComboBox<String> field = new JComboBox<>(FIELDS);
    private final JTextField query = new JTextField();
    private final JComboBox<String> status = new JComboBox<>(STATUSES);
    private final Timer searchTimer = new Timer(250, event -> notifySearch());
    private transient Consumer<Criteria> searchChanged = criteria -> {
    };

    public MemberSearchPanel() {
        setLayout(new BorderLayout(8, 0));
        setBackground(Theme.PAPER);
        field.setName("memberSearchField");
        query.setName("memberSearch");
        query.getAccessibleContext().setAccessibleName("Search members");
        status.setName("memberStatusFilter");
        add(field, BorderLayout.WEST);
        add(query, BorderLayout.CENTER);
        add(status, BorderLayout.EAST);
        query.getDocument().addDocumentListener(new DocumentListener() {
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
        field.addActionListener(event -> searchTimer.restart());
        status.addActionListener(event -> searchTimer.restart());
        query.addActionListener(event -> {
            searchTimer.stop();
            notifySearch();
        });
    }

    public void setSearchChanged(Consumer<Criteria> listener) {
        searchChanged = listener == null ? criteria -> {
        } : listener;
    }

    public Criteria criteria() {
        return new Criteria(MemberSearchField.values()[field.getSelectedIndex()], query.getText(),
                MemberStatusFilter.values()[status.getSelectedIndex()]);
    }

    private void notifySearch() {
        searchChanged.accept(criteria());
    }

    public record Criteria(MemberSearchField field, String query, MemberStatusFilter statusFilter) {
    }
}