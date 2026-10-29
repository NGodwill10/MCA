package ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import model.Member;
import services.SettingsDefaults;
import services.SettingsService;

public final class MemberDetailPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel memberId = new JLabel();
    private final JLabel name = new JLabel();
    private final JLabel phone = new JLabel();
    private final JLabel email = new JLabel();
    private final JLabel address = new JLabel();
    private final JLabel membershipType = new JLabel();
    private final JLabel joinDate = new JLabel();
    private final JLabel expiryDate = new JLabel();
    private final JLabel status = new JLabel();
    private final JLabel borrowingLimit = new JLabel();
    private final MemberCurrentlyBorrowedPanel borrowedBooks = new MemberCurrentlyBorrowedPanel();
    private final MemberLoanHistoryPanel loanHistory = new MemberLoanHistoryPanel();
    private final MemberDuesPanel dues = new MemberDuesPanel();
    private final transient SettingsService settings;
    private final JButton renew = new JButton("Renew membership");
    private transient Consumer<Member> renewHandler = member -> {
    };
    private transient Member currentMember;

    public MemberDetailPanel(Runnable onBack, SettingsService settings) {
        this.settings = settings;
        setLayout(new BorderLayout(0, Theme.GAP));
        setBackground(Theme.WHITE);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel header = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        header.setBackground(Theme.WHITE);
        JButton back = new JButton("Back to members");
        back.addActionListener(event -> onBack.run());
        renew.setVisible(false);
        renew.addActionListener(event -> {
            if (currentMember != null)
                renewHandler.accept(currentMember);
        });
        header.add(back);
        header.add(renew);
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setBackground(Theme.WHITE);
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
        JPanel profile = new JPanel(new GridLayout(0, 2, 8, 6));
        profile.setBackground(Theme.WHITE);
        profile.setBorder(BorderFactory.createTitledBorder("Profile"));
        addProfileRow(profile, "Member ID", memberId);
        addProfileRow(profile, "Name", name);
        addProfileRow(profile, "Phone", phone);
        addProfileRow(profile, "Email", email);
        addProfileRow(profile, "Address", address);
        addProfileRow(profile, "Membership type", membershipType);
        addProfileRow(profile, "Join date", joinDate);
        addProfileRow(profile, "Expiry date", expiryDate);
        addProfileRow(profile, "Status", status);
        content.add(profile);

        JPanel limit = new JPanel(new BorderLayout());
        limit.setBackground(Theme.WHITE);
        limit.setBorder(BorderFactory.createTitledBorder("Borrowing limit"));
        limit.add(borrowingLimit, BorderLayout.CENTER);
        content.add(limit);
        content.add(borrowedBooks);
        content.add(loanHistory);
        content.add(dues);
        add(new JScrollPane(content), BorderLayout.CENTER);
    }

    public void setMember(Member member) {
        currentMember = member;
        memberId.setText(member.memberId());
        name.setText(member.name());
        phone.setText(display(member.phone()));
        email.setText(display(member.email()));
        address.setText(display(member.address()));
        membershipType.setText(settings.getString(SettingsDefaults.key("membership.label.", member.membershipType())));
        joinDate.setText(member.joinDate().toString());
        expiryDate.setText(member.expiryDate().toString()
                + (member.expiredOn(LocalDate.now()) ? "  |  EXPIRED" : ""));
        status.setText(titleCase(member.status().name()));
        borrowingLimit.setText(settings.getInt(SettingsDefaults.key("limit.", member.membershipType())) + " books");
    }

    public void setBorrowedBooks(List<List<String>> rows) {
        borrowedBooks.setData(rows);
    }

    public void setLoanHistory(List<List<String>> rows) {
        loanHistory.setData(rows);
    }

    public void setDues(List<List<String>> rows) {
        dues.setData(rows);
    }

    public void setDues(List<List<String>> chargeRows, List<List<String>> paymentRows,
            String outstanding, String refundCredit) {
        dues.setData(chargeRows, paymentRows, outstanding, refundCredit);
    }

    public void setRenewHandler(boolean allowed, Consumer<Member> handler) {
        renewHandler = handler == null ? member -> {
        } : handler;
        renew.setVisible(allowed);
    }

    private static void addProfileRow(JPanel panel, String label, JLabel value) {
        JLabel labelComponent = new JLabel(label + ":");
        labelComponent.setHorizontalAlignment(SwingConstants.RIGHT);
        panel.add(labelComponent);
        panel.add(value);
    }

    private static String display(String value) {
        return value.isBlank() ? "Not provided" : value;
    }

    private static String titleCase(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
