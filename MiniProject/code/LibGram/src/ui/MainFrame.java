package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import model.Book;
import model.Member;
import model.User;
import services.CatalogService;
import services.AccountService;
import services.LendingService;
import services.LoanRules;
import services.MemberSearchField;
import services.MemberService;
import services.MemberStatusFilter;
import services.PermissionPolicy;
import services.SettingsService;
import services.ChargeService;
import services.MembershipService;
import services.PaymentService;
import services.ReturnService;
import services.LostBookService;
import services.ReservationService;
import services.NotificationService;
import services.AlertScanner;
import model.NotificationContext;

public final class MainFrame extends JPanel {
    private static final long serialVersionUID = 1L;

    private final transient User user;
    private final transient PermissionPolicy permissions;
    private final transient CatalogService catalogService;
    private final transient MemberService memberService;
    private final transient SettingsService settingsService;
    private final transient AccountService accountService;
    private final transient LendingService lendingService;
    private final transient ChargeService chargeService;
    private final transient MembershipService membershipService;
    private final transient PaymentService paymentService;
    private final transient ReturnService returnService;
    private final transient LostBookService lostBookService;
    private final transient ReservationService reservationService;
    private final transient NotificationService notificationService;
    private final transient AlertScanner alertScanner;
    private final transient Runnable onLogout;
    private MenuSection selectedSection = MenuSection.BOOKKEEPING;
    private final JPanel leftCards = new JPanel(new CardLayout());
    private final JPanel centerCards = new JPanel(new CardLayout());
    private final JPanel rightCards = new JPanel(new CardLayout());
    private final JPanel searchControlCards = new JPanel(new CardLayout());
    private final transient Map<MenuSection, JButton> menuButtons = new EnumMap<>(MenuSection.class);
    private final transient Map<String, JButton> bookActionButtons = new java.util.HashMap<>();
    private BookCatalogPanel bookCatalog;
    private MemberCatalogPanel memberCatalog;
    private MemberSearchPanel memberSearchPanel;
    private MemberDetailPanel memberDetailPanel;
    private LendingPanel lendingPanel;
    private ActiveLoansPanel activeLoansPanel;
    private ReservationsPanel reservationsPanel;
    private RecoveryPanel recoveryPanel;
    private DuesPanel duesPanel;

    public MainFrame(Window owner, User user, CatalogService catalogService, MemberService memberService,
            SettingsService settingsService,
            AccountService accountService,
            MembershipService membershipService,
            LendingService lendingService,
            ChargeService chargeService, PaymentService paymentService, ReturnService returnService,
            LostBookService lostBookService, ReservationService reservationService,
            Runnable onLogout, NotificationService notificationService, AlertScanner alertScanner) throws IOException {
        this.user = user;
        this.catalogService = catalogService;
        this.memberService = memberService;
        this.settingsService = settingsService;
        this.accountService = accountService;
        this.membershipService = membershipService;
        this.lendingService = lendingService;
        this.chargeService = chargeService;
        this.paymentService = paymentService;
        this.returnService = returnService;
        this.lostBookService = lostBookService;
        this.reservationService = reservationService;
        this.notificationService = notificationService;
        this.alertScanner = alertScanner;
        this.onLogout = onLogout;
        this.permissions = new PermissionPolicy();
        setLayout(new ProportionalLayout(false, 0.10, 0.90));
        setBackground(Theme.PAPER);
        add(new TitleBar(owner, user, permissions, settingsService, accountService, onLogout, alertScanner));
        add(buildBody());
        selectSection(MenuSection.BOOKKEEPING);
        updateBookActionButtons();
        settingsService.addListener(() -> {
            refreshMembers();
            repaint();
        });
    }

    private JPanel buildBody() throws IOException {
        JPanel body = new JPanel(new ProportionalLayout(true, 0.25, 0.50, 0.25));
        body.setBackground(Theme.PAPER);
        body.add(buildLeftPanel());
        body.add(buildCenterPanel());
        body.add(buildRightPanel());
        return body;
    }

    private JPanel buildLeftPanel() {
        JPanel panel = sectionPanel();
        panel.setLayout(new BorderLayout(0, Theme.GAP));
        panel.setBorder(BorderFactory.createEmptyBorder(Theme.PANEL_INSET, Theme.PANEL_INSET,
                Theme.PANEL_INSET, 10));
        JLabel heading = new JLabel();
        heading.setFont(Theme.HEADING);
        heading.setForeground(Theme.INK);
        panel.add(heading, BorderLayout.NORTH);
        leftCards.putClientProperty("heading", heading);
        for (MenuSection section : MenuSection.values()) {
            JPanel options = new JPanel();
            options.setBackground(Theme.PAPER);
            options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
            for (String option : section.options()) {
                JButton button = new JButton(option);
                button.setHorizontalAlignment(SwingConstants.LEFT);
                button.setAlignmentX(LEFT_ALIGNMENT);
                button.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 42));
                button.setToolTipText(option);
                boolean allowed = switch (section) {
                    case BOOKKEEPING -> permissions.canManageCatalog(user) || option.equals("View details");
                    case MEMBERS -> permissions.canManageMembers(user) || option.equals("View member details");
                    case LENDING, FEES_PAYMENTS, RECOVERY -> permissions.canCirculate(user);
                };
                button.setEnabled(allowed);
                if (section == MenuSection.MEMBERS && !permissions.canManageMembers(user)
                        && !option.equals("View member details")) {
                    button.setToolTipText("Only an admin can change members");
                }
                if (section == MenuSection.BOOKKEEPING) {
                    bookActionButtons.put(option, button);
                    button.setToolTipText(option);
                    button.addActionListener(event -> handleBookAction(option));
                } else if (section == MenuSection.MEMBERS) {
                    bookActionButtons.put("member:" + option, button);
                    button.addActionListener(event -> handleMemberAction(option));
                } else if (section == MenuSection.LENDING) {
                    button.addActionListener(event -> handleLendingAction(option));
                } else if (section == MenuSection.FEES_PAYMENTS) {
                    button.addActionListener(event -> handleFeesAction(option));
                } else {
                    button.addActionListener(event -> handleRecoveryAction(option));
                }
                options.add(button);
                options.add(Box.createVerticalStrut(8));
            }
            leftCards.add(options, section.name());
        }
        panel.add(leftCards, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCenterPanel() throws IOException {
        JPanel panel = sectionPanel();
        panel.setLayout(new BorderLayout(0, Theme.GAP));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 10, Theme.PANEL_INSET, 10));

        JPanel navigationAndSearch = new JPanel(new BorderLayout(0, Theme.GAP));
        navigationAndSearch.setBackground(Theme.PAPER);
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navigation.setBackground(Theme.PAPER);
        for (MenuSection section : MenuSection.values()) {
            JButton button = new JButton(section.label());
            button.setFocusPainted(false);
            button.setMnemonic(section.label().charAt(0));
            button.addActionListener(event -> selectSection(section));
            menuButtons.put(section, button);
            navigation.add(button);
        }
        navigationAndSearch.add(navigation, BorderLayout.NORTH);

        JPanel search = new JPanel(new BorderLayout(8, 0));
        search.setBackground(Theme.PAPER);
        JLabel searchLabel = new JLabel("Search");
        JComboBox<String> searchFieldSelector = new JComboBox<>(new String[] {
                "All", "Title", "Author", "ISBN", "Publisher", "Category", "Language", "Year" });
        JTextField searchField = new JTextField();
        searchField.setName("catalogSearch");
        searchField.getAccessibleContext().setAccessibleName("Search the current section");
        JCheckBox availableOnly = new JCheckBox("Available only");
        availableOnly.setBackground(Theme.PAPER);
        searchField.addActionListener(event -> applyCurrentSearch(searchFieldSelector, searchField, availableOnly));
        JPanel query = new JPanel(new BorderLayout(8, 0));
        query.setBackground(Theme.PAPER);
        query.add(searchFieldSelector, BorderLayout.WEST);
        query.add(searchField, BorderLayout.CENTER);
        search.add(searchLabel, BorderLayout.WEST);
        search.add(query, BorderLayout.CENTER);
        search.add(availableOnly, BorderLayout.EAST);
        searchControlCards.add(search, "BOOKS");
        memberSearchPanel = new MemberSearchPanel();
        memberSearchPanel.setSearchChanged(this::applyMemberSearch);
        searchControlCards.add(memberSearchPanel, "MEMBERS");
        searchControlCards.add(new JPanel(), "NONE");
        navigationAndSearch.add(searchControlCards, BorderLayout.SOUTH);
        panel.add(navigationAndSearch, BorderLayout.NORTH);

        bookCatalog = new BookCatalogPanel(catalogService.loadBooks());
        bookCatalog.setSelectionChanged(this::updateBookActionButtons);
        memberCatalog = new MemberCatalogPanel(memberService.listSorted(user), settingsService);
        memberCatalog.setSelectionChanged(this::updateMemberActionButtons);
        memberDetailPanel = new MemberDetailPanel(this::showMembersList, settingsService);
        memberDetailPanel.setRenewHandler(permissions.canManageMembers(user), this::renewMembership);
        lendingPanel = new LendingPanel(user, catalogService, memberService, lendingService, reservationService);
        activeLoansPanel = new ActiveLoansPanel(lendingService);
        duesPanel = new DuesPanel(user, memberService, chargeService, paymentService, settingsService);
        recoveryPanel = new RecoveryPanel(user, memberService, catalogService, lendingService, returnService,
                lostBookService, chargeService, paymentService, settingsService);
        reservationsPanel = new ReservationsPanel(user, reservationService, memberService, catalogService);
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent event) {
                applyCurrentSearch(searchFieldSelector, searchField, availableOnly);
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent event) {
                applyCurrentSearch(searchFieldSelector, searchField, availableOnly);
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent event) {
                applyCurrentSearch(searchFieldSelector, searchField, availableOnly);
            }
        });
        searchFieldSelector.addActionListener(event -> applyCurrentSearch(searchFieldSelector, searchField, availableOnly));
        availableOnly.addActionListener(event -> applyCurrentSearch(searchFieldSelector, searchField, availableOnly));
        centerCards.add(bookCatalog, MenuSection.BOOKKEEPING.name());
        centerCards.add(memberCatalog, MenuSection.MEMBERS.name());
        centerCards.add(memberDetailPanel, "MEMBER_DETAIL");
        centerCards.add(lendingPanel, "LENDING");
        centerCards.add(activeLoansPanel, "ACTIVE_LOANS");
        centerCards.add(duesPanel, MenuSection.FEES_PAYMENTS.name());
        centerCards.add(recoveryPanel, MenuSection.RECOVERY.name());
        centerCards.add(reservationsPanel, "RESERVATIONS");
        panel.add(centerCards, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRightPanel() {
        JPanel panel = sectionPanel();
        panel.setLayout(new BorderLayout(0, Theme.GAP));
        panel.setBorder(BorderFactory.createEmptyBorder(Theme.PANEL_INSET, 10,
                Theme.PANEL_INSET, Theme.PANEL_INSET));
        JLabel heading = new JLabel("Notifications");
        heading.setFont(Theme.HEADING);
        heading.setForeground(Theme.INK);
        panel.add(heading, BorderLayout.NORTH);
        for (MenuSection section : MenuSection.values()) {
            rightCards.add(new NotificationPanel(notificationService, switch (section) {
                case BOOKKEEPING -> NotificationContext.BOOKKEEPING;
                case MEMBERS -> NotificationContext.MEMBERS;
                case LENDING -> NotificationContext.LENDING;
                case FEES_PAYMENTS -> NotificationContext.LENDING;
                case RECOVERY -> NotificationContext.RECOVERY;
            }, this::viewNotification), section.name());
        }
        panel.add(rightCards, BorderLayout.CENTER);
        return panel;
    }

    private void selectSection(MenuSection section) {
        selectedSection = section;
        alertScanner.scanContext(switch (section) {
            case BOOKKEEPING -> NotificationContext.BOOKKEEPING;
            case MEMBERS -> NotificationContext.MEMBERS;
            case LENDING -> NotificationContext.LENDING;
            case FEES_PAYMENTS -> NotificationContext.LENDING;
            case RECOVERY -> NotificationContext.RECOVERY;
        });
        ((CardLayout) leftCards.getLayout()).show(leftCards, section.name());
        ((CardLayout) centerCards.getLayout()).show(centerCards, section.name());
        ((CardLayout) rightCards.getLayout()).show(rightCards, section.name());
        JLabel heading = (JLabel) leftCards.getClientProperty("heading");
        heading.setText(section.label());
        ((CardLayout) searchControlCards.getLayout()).show(searchControlCards,
                section == MenuSection.MEMBERS ? "MEMBERS" : section == MenuSection.LENDING ? "NONE" : "BOOKS");
        if (section == MenuSection.MEMBERS) {
            refreshMembers();
        }
        if (section == MenuSection.LENDING) {
            duesPanel.refreshMembers();
            alertScanner.scanAsync();
        } else if (section == MenuSection.FEES_PAYMENTS) {
            duesPanel.refreshMembers();
        } else if (section == MenuSection.RECOVERY) {
            recoveryPanel.refreshMembers();
            alertScanner.scanAsync();
        }
        menuButtons.forEach((menu, button) -> {
            boolean selected = menu == section;
            button.setBackground(selected ? Theme.ACCENT : Theme.WHITE);
            button.setForeground(selected ? Theme.WHITE : Theme.INK);
            button.setOpaque(true);
        });
        updateBadges();
        for (java.awt.Component component : rightCards.getComponents()) {
            if (component.isVisible() && component instanceof NotificationPanel panel)
                panel.refresh();
        }
        revalidate();
        repaint();
    }

    private void applyCurrentSearch(JComboBox<String> field, JTextField query, JCheckBox availableOnly) {
        if (selectedSection == MenuSection.FEES_PAYMENTS) {
            duesPanel.setPendingSearch(query.getText());
        } else if (selectedSection == MenuSection.RECOVERY) {
            recoveryPanel.setLoanSearch(query.getText());
        } else {
            applyBookFilters(field, query, availableOnly);
        }
    }

    private void updateBadges() {
        new javax.swing.SwingWorker<Map<MenuSection, Long>, Void>() {
            @Override
            protected Map<MenuSection, Long> doInBackground() throws Exception {
                Map<MenuSection, Long> counts = new EnumMap<>(MenuSection.class);
                for (MenuSection section : MenuSection.values())
                    counts.put(section, notificationService.countUndismissed(toContext(section)));
                return counts;
            }

            @Override
            protected void done() {
                try {
                    get().forEach((section, count) -> menuButtons.get(section)
                            .setText(section.label() + (count == 0 ? "" : "  [" + count + "]")));
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private static NotificationContext toContext(MenuSection section) {
        return switch (section) {
            case BOOKKEEPING -> NotificationContext.BOOKKEEPING;
            case MEMBERS -> NotificationContext.MEMBERS;
            case LENDING -> NotificationContext.LENDING;
            case FEES_PAYMENTS -> NotificationContext.LENDING;
            case RECOVERY -> NotificationContext.RECOVERY;
        };
    }

    private void handleLendingAction(String action) {
        if ("New lending".equals(action)) {
            ((CardLayout) centerCards.getLayout()).show(centerCards, "LENDING");
        } else if ("Active loans".equals(action)) {
            activeLoansPanel.refresh();
            ((CardLayout) centerCards.getLayout()).show(centerCards, "ACTIVE_LOANS");
        } else if ("Collect dues".equals(action)) {
            duesPanel.refreshMembers();
            ((CardLayout) centerCards.getLayout()).show(centerCards, MenuSection.FEES_PAYMENTS.name());
        } else if ("Reservations".equals(action)) {
            reservationsPanel.refresh();
            ((CardLayout) centerCards.getLayout()).show(centerCards, "RESERVATIONS");
        }
    }

    private void handleFeesAction(String action) {
        duesPanel.showPaymentView("Payment history".equals(action));
        duesPanel.refreshMembers();
        ((CardLayout) centerCards.getLayout()).show(centerCards, MenuSection.FEES_PAYMENTS.name());
    }

    private void handleRecoveryAction(String action) {
        if ("Replace member card".equals(action)) {
            recoveryPanel.replaceMemberCard();
            return;
        }
        recoveryPanel.setMode(action);
        if ("Mark lost".equals(action)) {
            recoveryPanel.markSelectedLost();
        }
        ((CardLayout) centerCards.getLayout()).show(centerCards, MenuSection.RECOVERY.name());
    }

    private void viewNotification(model.Notification notification) {
        try {
            selectSection(switch (notification.context()) {
                case BOOKKEEPING -> MenuSection.BOOKKEEPING;
                case MEMBERS -> MenuSection.MEMBERS;
                case LENDING -> MenuSection.LENDING;
                case RECOVERY -> MenuSection.RECOVERY;
            });
            switch (notification.context()) {
                case BOOKKEEPING ->
                    catalogService.loadBooks().stream().filter(book -> book.bookId().equals(notification.refId()))
                            .findFirst().ifPresent(this::showBookDetails);
                case MEMBERS -> memberService.listSorted(user).stream()
                        .filter(member -> member.memberId().equals(notification.refId()))
                        .findFirst().ifPresent(this::showMemberDetails);
                case LENDING -> {
                    activeLoansPanel.refresh();
                    ((CardLayout) centerCards.getLayout()).show(centerCards, "ACTIVE_LOANS");
                }
                case RECOVERY -> ((CardLayout) centerCards.getLayout()).show(centerCards, MenuSection.RECOVERY.name());
            }
        } catch (IOException exception) {
            JOptionPane.showMessageDialog(this, "The related item could not be opened.", "Notification",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void applyBookFilters(JComboBox<String> field, JTextField query, JCheckBox availableOnly) {
        bookCatalog.setFilters((String) field.getSelectedItem(), query.getText(), availableOnly.isSelected());
    }

    private void handleBookAction(String action) {
        var selectedBook = bookCatalog.selectedBook();
        switch (action) {
            case "Add book" -> openBookForm(null);
            case "Update book" -> selectedBook.ifPresent(this::openBookForm);
            case "Remove book" -> selectedBook.ifPresent(this::confirmRemoveBook);
            case "View details" -> selectedBook.ifPresent(this::showBookDetails);
            default -> throw new IllegalArgumentException("Unknown catalog action: " + action);
        }
    }

    private void handleMemberAction(String action) {
        var selectedMember = memberCatalog.selectedMember();
        switch (action) {
            case "Add member" -> openMemberForm(null);
            case "Update member" -> selectedMember.ifPresent(this::openMemberForm);
            case "Remove member" -> selectedMember.ifPresent(this::removeMember);
            case "View member details" -> selectedMember.ifPresent(this::showMemberDetails);
            default -> throw new IllegalArgumentException("Unknown member action: " + action);
        }
    }

    private void openMemberForm(Member existing) {
        MemberFormDialog dialog = new MemberFormDialog(SwingUtilities.getWindowAncestor(this), user, memberService,
                settingsService,
                existing, this::showMemberDetails);
        if (dialog.showDialog().isPresent()) {
            refreshMembers();
        }
    }

    private void removeMember(Member member) {
        Object[] choices = { "Deactivate", "Delete permanently", "Cancel" };
        int choice = JOptionPane.showOptionDialog(this,
                "Deactivate keeps the member record and is recommended. Permanent deletion cannot be undone.",
                "Remove member", JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE,
                null, choices, choices[0]);
        if (choice == 0) {
            changeMemberStatus(member, false);
        } else if (choice == 1) {
            int confirmed = JOptionPane.showConfirmDialog(this,
                    "Permanently delete " + member.name() + "? This cannot be undone.",
                    "Delete member", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirmed == JOptionPane.YES_OPTION) {
                try {
                    memberService.delete(user, member.memberId());
                    refreshMembers();
                } catch (IllegalArgumentException | SecurityException exception) {
                    showMemberError(exception);
                } catch (IOException exception) {
                    logMemberError(exception);
                    JOptionPane.showMessageDialog(this, "The member could not be deleted.",
                            "Member error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private void changeMemberStatus(Member member, boolean reactivate) {
        try {
            if (reactivate) {
                memberService.reactivate(user, member.memberId());
            } else {
                memberService.deactivate(user, member.memberId());
            }
            refreshMembers();
        } catch (IllegalArgumentException | SecurityException exception) {
            showMemberError(exception);
        } catch (IOException exception) {
            logMemberError(exception);
            JOptionPane.showMessageDialog(this, "The member status could not be changed.",
                    "Member error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showMemberDetails(Member member) {
        memberDetailPanel.setMember(member);
        List<List<String>> activeRows = lendingService.activeLoansForMember(member.memberId()).stream()
                .map(loan -> List.of(loan.bookTitleSnapshot(), loan.issueDate().toString(), loan.dueDate().toString(),
                        Long.toString(LoanRules.daysOverdue(loan, java.time.LocalDate.now()))))
                .toList();
        List<List<String>> historyRows = lendingService.loansForMember(member.memberId()).stream()
                .map(loan -> List.of(loan.bookTitleSnapshot(), loan.issueDate().toString(), loan.dueDate().toString(),
                        loan.status().name()))
                .toList();
        memberDetailPanel.setBorrowedBooks(activeRows);
        memberDetailPanel.setLoanHistory(historyRows);
        List<model.Charge> memberCharges = chargeService.forMember(member.memberId());
        List<List<String>> chargeRows = memberCharges.stream().map(charge -> List.of(
                charge.createdAt().toLocalDate().toString(), charge.description(), charge.type().name(),
                formatMoney(charge.amount()), formatMoney(charge.waivedAmount()), formatMoney(charge.paidAmount()),
                formatMoney(charge.balance()), charge.status().name())).toList();
        List<List<String>> paymentRows = paymentService.history(member.memberId()).stream().map(payment -> List.of(
                payment.paidAt().toString(), formatMoney(payment.amount()), payment.method().name(),
                payment.receivedBy(), payment.paymentId())).toList();
        memberDetailPanel.setDues(chargeRows, paymentRows, formatMoney(chargeService.unpaidBalance(member.memberId())),
                formatMoney(chargeService.refundDue(member.memberId())));
        ((CardLayout) centerCards.getLayout()).show(centerCards, "MEMBER_DETAIL");
    }

    private void renewMembership(Member member) {
        if (JOptionPane.showConfirmDialog(this, "Renew membership for " + member.name() + "?",
                "Renew membership", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) != JOptionPane.YES_OPTION)
            return;
        try {
            Member renewed = membershipService.renew(user, member.memberId());
            showMemberDetails(renewed);
            refreshMembers();
            JOptionPane.showMessageDialog(this, "Membership renewed through " + renewed.expiryDate() + ".",
                    "Membership", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException | SecurityException exception) {
            showMemberError(exception);
        } catch (IOException exception) {
            logMemberError(exception);
            JOptionPane.showMessageDialog(this, "Membership could not be renewed.", "Membership",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String formatMoney(java.math.BigDecimal amount) {
        return "Rs. " + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private void showMembersList() {
        ((CardLayout) centerCards.getLayout()).show(centerCards, MenuSection.MEMBERS.name());
        refreshMembers();
    }

    private void applyMemberSearch(MemberSearchPanel.Criteria criteria) {
        try {
            List<Member> visible = memberService.search(user, criteria.field(), criteria.query(),
                    criteria.statusFilter());
            memberCatalog.setMembers(visible, memberService.listSorted(user).size());
            updateMemberActionButtons();
        } catch (SecurityException exception) {
            showMemberError(exception);
        } catch (IOException exception) {
            logMemberError(exception);
            JOptionPane.showMessageDialog(this, "Members could not be searched.",
                    "Member error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshMembers() {
        if (memberCatalog == null) {
            return;
        }
        try {
            MemberSearchPanel.Criteria criteria = memberSearchPanel.criteria();
            List<Member> visible = memberService.search(user, criteria.field(), criteria.query(),
                    criteria.statusFilter());
            memberCatalog.setMembers(visible, memberService.listSorted(user).size());
            updateMemberActionButtons();
        } catch (SecurityException exception) {
            showMemberError(exception);
        } catch (IOException exception) {
            logMemberError(exception);
            JOptionPane.showMessageDialog(this, "The member list could not be refreshed.",
                    "Member error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateMemberActionButtons() {
        boolean selected = memberCatalog != null && memberCatalog.selectedMember().isPresent();
        boolean canManage = permissions.canManageMembers(user);
        JButton add = bookActionButtons.get("member:Add member");
        if (add != null) {
            add.setEnabled(canManage);
            add.setToolTipText(canManage ? "Add member" : "Only an admin can change members");
        }
        for (String action : List.of("Update member", "Remove member", "View member details")) {
            JButton button = bookActionButtons.get("member:" + action);
            if (button != null) {
                boolean viewAction = action.equals("View member details");
                button.setEnabled(selected && (viewAction || canManage));
                button.setToolTipText(!canManage && !viewAction
                        ? "Only an admin can change members"
                        : selected ? action : "Select a member first");
            }
        }
    }

    private void showMemberError(RuntimeException exception) {
        JOptionPane.showMessageDialog(this, exception.getMessage(), "Member error", JOptionPane.ERROR_MESSAGE);
    }

    private static void logMemberError(Exception exception) {
        System.getLogger(MainFrame.class.getName()).log(System.Logger.Level.ERROR,
                "Member operation failed", exception);
    }

    private void openBookForm(Book existing) {
        BookFormDialog dialog = new BookFormDialog(SwingUtilities.getWindowAncestor(this), user, catalogService,
                existing, this::showBookDetails);
        if (dialog.showDialog().isPresent()) {
            refreshBooks();
        }
    }

    private void confirmRemoveBook(Book book) {
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove \"" + book.title() + "\" from the catalog? This cannot be undone.",
                "Remove book", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            catalogService.removeBook(user, book.bookId());
            refreshBooks();
        } catch (IllegalArgumentException | SecurityException exception) {
            showCatalogError(exception);
        } catch (IOException exception) {
            logCatalogError(exception);
            JOptionPane.showMessageDialog(this, "The book could not be removed. Check local storage and try again.",
                    "Catalog error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showBookDetails(Book book) {
        String details = "Title: " + book.title() + "\nAuthors: " + book.authors() + "\nISBN: " + book.isbn()
                + "\nPublisher: " + book.publisher() + "\nYear: " + book.publicationYear()
                + "\nEdition: " + book.edition() + "\nCategory: " + book.category() + "\nLanguage: "
                + book.language() + "\nShelf: " + book.shelfLocation() + "\nReplacement price: Rs. "
                + book.price().setScale(2, java.math.RoundingMode.HALF_UP) + "\nCopies: "
                + book.availableCopies() + " available / " + book.totalCopies() + " total\nDate added: "
                + book.dateAdded();
        JOptionPane.showMessageDialog(this, details, "Book details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void refreshBooks() {
        try {
            bookCatalog.setBooks(catalogService.loadBooks());
            updateBookActionButtons();
        } catch (IOException exception) {
            logCatalogError(exception);
            JOptionPane.showMessageDialog(this, "The catalog could not be refreshed.",
                    "Catalog error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateBookActionButtons() {
        boolean selected = bookCatalog != null && bookCatalog.selectedBook().isPresent();
        for (String action : List.of("Update book", "Remove book", "View details")) {
            JButton button = bookActionButtons.get(action);
            if (button != null) {
                button.setEnabled(selected && (action.equals("View details")
                        || permissions.canManageCatalog(user)));
                button.setToolTipText(selected ? action : "Select a book first");
            }
        }
    }

    private void showCatalogError(RuntimeException exception) {
        JOptionPane.showMessageDialog(this, exception.getMessage(), "Catalog error", JOptionPane.ERROR_MESSAGE);
    }

    private static void logCatalogError(Exception exception) {
        System.getLogger(MainFrame.class.getName()).log(System.Logger.Level.ERROR,
                "Catalog operation failed", exception);
    }

    private static JPanel sectionPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(Theme.PAPER);
        return panel;
    }
}
