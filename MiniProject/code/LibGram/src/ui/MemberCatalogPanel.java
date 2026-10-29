package ui;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SortOrder;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import model.Member;
import model.MemberStatus;
import model.MembershipType;
import services.SettingsDefaults;
import services.SettingsService;

public final class MemberCatalogPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final transient List<Member> members = new ArrayList<>();
    private final MemberTableModel tableModel = new MemberTableModel(members);
    private final JTable table = new JTable(tableModel);
    private final transient TableRowSorter<MemberTableModel> sorter = new TableRowSorter<>(tableModel);
    private final java.awt.CardLayout contentLayout = new java.awt.CardLayout();
    private final JPanel content = new JPanel(contentLayout);
    private final JLabel count = new JLabel();
    private final JLabel emptyMessage = new JLabel("No members yet. Use Add member on the left.",
            SwingConstants.CENTER);
    private transient Runnable selectionChanged = () -> {
    };
    private final transient SettingsService settings;

    public MemberCatalogPanel(List<Member> members, SettingsService settings) {
        this.settings = settings;
        buildPanel();
        setMembers(members, members.size());
    }

    public Optional<Member> selectedMember() {
        int selectedRow = table.getSelectedRow();
        return selectedRow < 0 ? Optional.empty()
                : Optional.of(tableModel.memberAt(table.convertRowIndexToModel(selectedRow)));
    }

    public void setSelectionChanged(Runnable listener) {
        selectionChanged = listener;
    }

    public void setMembers(List<Member> visibleMembers, int totalMembers) {
        String selectedMemberId = selectedMember().map(Member::memberId).orElse(null);
        members.clear();
        members.addAll(visibleMembers);
        tableModel.fireTableDataChanged();
        if (selectedMemberId == null) {
            table.clearSelection();
        } else {
            int selectedModelRow = -1;
            for (int row = 0; row < members.size(); row++) {
                if (selectedMemberId.equals(members.get(row).memberId())) {
                    selectedModelRow = row;
                    break;
                }
            }
            if (selectedModelRow >= 0) {
                int selectedViewRow = table.convertRowIndexToView(selectedModelRow);
                table.setRowSelectionInterval(selectedViewRow, selectedViewRow);
            } else {
                table.clearSelection();
            }
        }
        emptyMessage.setText(totalMembers == 0
                ? "No members yet. Use Add member on the left."
                : "No members match these filters.");
        count.setText(members.size() + " of " + totalMembers + " members");
        contentLayout.show(content, members.isEmpty() ? "empty" : "table");
        selectionChanged.run();
    }

    private void buildPanel() {
        setLayout(new BorderLayout(0, Theme.GAP));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createLineBorder(Theme.LINE));
        table.setRowSorter(sorter);
        table.setFillsViewportHeight(true);
        table.setRowHeight(28);
        table.setFont(Theme.BODY);
        table.setShowVerticalLines(false);
        sorter.setComparator(1, String.CASE_INSENSITIVE_ORDER);
        sorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(1, SortOrder.ASCENDING)));
        table.setDefaultRenderer(Object.class, new MemberCellRenderer());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectionChanged.run();
            }
        });

        JPanel empty = new JPanel(new BorderLayout());
        empty.setBackground(Theme.WHITE);
        emptyMessage.setForeground(Theme.MUTED);
        empty.add(emptyMessage, BorderLayout.CENTER);
        content.setBackground(Theme.WHITE);
        content.add(new JScrollPane(table), "table");
        content.add(empty, "empty");
        add(content, BorderLayout.CENTER);
        count.setForeground(Theme.MUTED);
        count.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
        add(count, BorderLayout.SOUTH);
    }

    private final class MemberCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public java.awt.Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                boolean focused, int row, int column) {
            super.getTableCellRendererComponent(source, value, selected, focused, row, column);
            int modelRow = source.convertRowIndexToModel(row);
            Member member = members.get(modelRow);
            if (!selected) {
                setForeground(member.status() == MemberStatus.INACTIVE ? Theme.MUTED : Theme.INK);
                setBackground(row % 2 == 0 ? Theme.WHITE : Theme.PAPER);
            }
            if (column == 5 && member.expiredOn(java.time.LocalDate.now())) {
                setText(member.status().name() + "  |  EXPIRED");
            }
            return this;
        }
    }

    private final class MemberTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 1L;
        private static final String[] COLUMNS = {
                "Member ID", "Name", "Phone", "Email", "Type", "Status", "Expiry" };
        private final transient List<Member> members;

        private MemberTableModel(List<Member> members) {
            this.members = members;
        }

        @Override
        public int getRowCount() {
            return members.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Member member = members.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> member.memberId();
                case 1 -> member.name();
                case 2 -> member.phone();
                case 3 -> member.email();
                case 4 -> displayType(member.membershipType());
                case 5 -> displayStatus(member);
                case 6 -> member.expiryDate().toString();
                default -> "";
            };
        }

        private Member memberAt(int row) {
            return members.get(row);
        }

        private String displayType(MembershipType type) {
            return settings.getString(SettingsDefaults.key("membership.label.", type));
        }

        private static String displayStatus(Member member) {
            String status = member.status().name().charAt(0)
                    + member.status().name().substring(1).toLowerCase(java.util.Locale.ROOT);
            return member.expiredOn(java.time.LocalDate.now()) ? status + "  |  EXPIRED" : status;
        }
    }
}
