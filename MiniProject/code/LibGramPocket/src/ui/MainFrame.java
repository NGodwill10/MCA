package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SortOrder;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import model.Contact;
import model.Book;
import model.LoanDirection;
import model.Notification;
import model.NotificationContext;
import model.NotificationType;
import model.User;
import services.BookService;
import services.AuthService;
import services.AlertScanner;
import services.ContactService;
import services.LendingService;
import services.LendingValidationException;
import services.NotificationService;
import services.BookSearchField;
import services.ContactSearchField;
import services.SettingsService;

public final class MainFrame extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JPanel leftCards = new JPanel(new CardLayout());
    private final JPanel centerCards = new JPanel(new CardLayout());
    private final JPanel rightCards = new JPanel(new CardLayout());
    private final transient Map<MenuSection, JButton> menuButtons = new EnumMap<>(MenuSection.class);
    private final transient Map<String, JButton> bookActionButtons = new HashMap<>();
    private final transient BookCatalogPanel catalogPanel;
    private final transient ContactCatalogPanel contactCatalogPanel;
    private final transient ContactDetailPanel contactDetailPanel;
    private final transient LendCartPanel lendCartPanel;
    private final transient LendBookPickerPanel lendBookPickerPanel;
    private final transient ContactPicker lendContactPicker;
    private final transient LoanListPanel activeLendsPanel;
    private final transient LoanListPanel lendHistoryPanel;
    private final transient LoanListPanel activeBorrowsPanel;
    private final transient LoanListPanel borrowHistoryPanel;
    private final transient BorrowBookPanel borrowBookPanel;
    private final transient BookService bookService;
    private final transient ContactService contactService;
    private final transient SettingsService settingsService;
    private final transient LendingService lendingService;
    private final transient NotificationService notificationService;
    private final transient AlertScanner alertScanner;
    private final transient Map<NotificationContext, NotificationPanel> notificationPanels = new EnumMap<>(
            NotificationContext.class);
    private final transient Consumer<NotificationContext> notificationListener;
    private final transient User user;
    private final JComboBox<String> searchFieldSelector = new JComboBox<>(new String[] {
            "All", "Title", "Author", "ISBN", "Publisher", "Category", "Language", "Year", "Reading status" });
    private final JTextField searchField = new JTextField();
    private final JCheckBox availableOnly = new JCheckBox("Available only");
    private final JCheckBox showBorrowed = new JCheckBox("Show borrowed (incl. returned)");
    private JPanel searchPanel;
    private final Timer searchTimer = new Timer(250, event -> applySearch());
    private final Timer notificationTimer;

    private MenuSection contactSourceSection = MenuSection.LENDING;
    private MenuSection currentSection = MenuSection.BOOKKEEPING;
    private boolean contactsSearchMode;
    private boolean lendFlow;
    private boolean notificationUiReady;
    private boolean notificationScanAllPending;
    private boolean notificationScanRunning;
    private transient SwingWorker<Void, Void> notificationWorker;
    private final EnumSet<NotificationContext> pendingNotificationContexts = EnumSet.noneOf(NotificationContext.class);
    private String pricingSettingsSignature;

    public MainFrame(Window owner, User user, AuthService authService, Path storageDirectory,
            BookService bookService, ContactService contactService,
            SettingsService settingsService, LendingService lendingService, NotificationService notificationService,
            AlertScanner alertScanner,
            Runnable onLogout) throws IOException {
        this.user = user;
        this.bookService = bookService;
        this.contactService = contactService;
        this.settingsService = settingsService;
        this.lendingService = lendingService;
        this.notificationService = notificationService;
        this.alertScanner = alertScanner;
        notificationListener = this::notificationChanged;
        catalogPanel = new BookCatalogPanel(bookService.listSorted());
        catalogPanel.setSelectionChanged(this::updateBookActionButtons);
        restoreCatalogSort();
        catalogPanel.setSortChanged(this::saveCatalogSort);
        contactCatalogPanel = new ContactCatalogPanel(contactService.listSorted());
        contactCatalogPanel.setSelectionChanged(this::updateContactActionButtons);
        contactDetailPanel = new ContactDetailPanel(this::showContactCatalog);
        lendCartPanel = new LendCartPanel(settingsService, settingsService.pricingFor("lend"));
        lendCartPanel.setConfirmHandler(this::confirmLend);
        lendBookPickerPanel = new LendBookPickerPanel();
        lendBookPickerPanel.setAddHandler(this::addToLendCart);
        lendBookPickerPanel.setLenderNameResolver(this::contactName);
        lendContactPicker = new ContactPicker(contactService);
        activeLendsPanel = new LoanListPanel(true, lendingService, this::contactName, settingsService);
        lendHistoryPanel = new LoanListPanel(false, lendingService, this::contactName, settingsService);
        activeBorrowsPanel = new LoanListPanel(true, true, lendingService, this::contactName, settingsService);
        borrowHistoryPanel = new LoanListPanel(false, true, lendingService, this::contactName, settingsService);
        borrowBookPanel = new BorrowBookPanel(bookService, contactService, lendingService, settingsService);
        for (MenuSection section : MenuSection.values()) {
            NotificationContext context = notificationContext(section);
            notificationPanels.put(context, new NotificationPanel(notificationService, context, section.label(),
                    this::openNotification, this::updateMenuBadge));
        }
        notificationService.addChangeListener(notificationListener);
        borrowBookPanel.setCompletedHandler(() -> {
            refreshBorrowViews();
            applySearchNow();
        });
        activeLendsPanel.setActionHandler(loan -> openReturnDialog(loan.loanId()));
        lendHistoryPanel.setActionHandler(this::markAsPaid);
        activeBorrowsPanel.setActionHandler(loan -> openReturnDialog(loan.loanId(), LoanDirection.BORROWED));
        borrowHistoryPanel.setActionHandler(this::markAsPaid);
        activeLendsPanel.setBackHandler(() -> selectSection(MenuSection.LENDING));
        lendHistoryPanel.setBackHandler(() -> selectSection(MenuSection.LENDING));
        activeBorrowsPanel.setBackHandler(() -> selectSection(MenuSection.BORROWING));
        borrowHistoryPanel.setBackHandler(() -> selectSection(MenuSection.BORROWING));
        notificationTimer = new Timer(AlertScanner.SCAN_INTERVAL_MILLIS, event -> scanNotifications(null));
        notificationTimer.setRepeats(true);
        setLayout(new ProportionalLayout(false, 0.10, 0.90));
        setBackground(Theme.PAPER);
        add(new TitleBar(owner, user, settingsService, authService, storageDirectory, () -> {
            stopNotificationTimer();
            onLogout.run();
        }));
        add(buildBody());
        pricingSettingsSignature = pricingSettingsSignature();
        settingsService.addListener(this::refreshPricingIfChanged);
        selectSection(settingsService.getEnum("ui.lastMenu", MenuSection.class));
        updateBookActionButtons();
        notificationUiReady = true;
        notificationTimer.start();
        scanNotifications(null);
    }

    private JPanel buildBody() {
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
                if (section == MenuSection.BOOKKEEPING) {
                    button.setToolTipText(option);
                    bookActionButtons.put(option, button);
                    button.addActionListener(event -> handleBookAction(option));
                } else if (option.equals("Contacts")) {
                    button.setToolTipText("Manage contacts");
                    button.addActionListener(event -> openContactsManager(section));
                } else if (option.equals("New lend")) {
                    button.setToolTipText(option);
                    button.addActionListener(event -> beginNewLend());
                } else if (option.equals("New borrow")) {
                    button.setToolTipText(option);
                    button.addActionListener(event -> showNewFlow(section, option));
                } else if (section == MenuSection.LENDING && option.equals("Receive a return")) {
                    button.setToolTipText(option);
                    button.addActionListener(event -> openReturnDialog(null));
                } else if (section == MenuSection.LENDING && option.equals("Active lends")) {
                    button.setToolTipText(option);
                    button.addActionListener(event -> showLendList("LEND_ACTIVE", activeLendsPanel));
                } else if (section == MenuSection.LENDING && option.equals("Lend history")) {
                    button.setToolTipText(option);
                    button.addActionListener(event -> showLendList("LEND_HISTORY", lendHistoryPanel));
                } else if (section == MenuSection.BORROWING && option.equals("Return to lender")) {
                    button.setToolTipText(option);
                    button.addActionListener(event -> openReturnDialog(null, LoanDirection.BORROWED));
                } else if (section == MenuSection.BORROWING && option.equals("Active borrows")) {
                    button.setToolTipText(option);
                    button.addActionListener(event -> showLoanList("BORROW_ACTIVE", activeBorrowsPanel,
                            MenuSection.BORROWING));
                } else if (section == MenuSection.BORROWING && option.equals("Borrow history")) {
                    button.setToolTipText(option);
                    button.addActionListener(event -> showLoanList("BORROW_HISTORY", borrowHistoryPanel,
                            MenuSection.BORROWING));
                } else {
                    button.setToolTipText(option + " (placeholder in Phase 2)");
                    button.addActionListener(event -> {
                        selectSection(section);
                        JOptionPane.showMessageDialog(this,
                                option + " will be implemented in a later phase.", section.label(),
                                JOptionPane.INFORMATION_MESSAGE);
                    });
                }
                options.add(button);
                options.add(Box.createVerticalStrut(8));
            }
            leftCards.add(options, section.name());
        }
        leftCards.add(buildContactActions(), "CONTACT_ACTIONS");
        leftCards.add(lendCartPanel, "LEND_CART");
        panel.add(leftCards, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildCenterPanel() {
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
        searchPanel = new JPanel(new BorderLayout(8, 0));
        searchPanel.setBackground(Theme.PAPER);
        JLabel searchLabel = new JLabel("Search");
        searchField.setName("catalogSearch");
        searchField.getAccessibleContext().setAccessibleName("Search books");
        availableOnly.setBackground(Theme.PAPER);
        JPanel queryControls = new JPanel(new BorderLayout(8, 0));
        queryControls.setBackground(Theme.PAPER);
        queryControls.add(searchFieldSelector, BorderLayout.WEST);
        queryControls.add(searchField, BorderLayout.CENTER);
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(queryControls, BorderLayout.CENTER);
        JPanel filters = new JPanel(new java.awt.GridLayout(0, 1));
        filters.setBackground(Theme.PAPER);
        filters.add(availableOnly);
        filters.add(showBorrowed);
        searchPanel.add(filters, BorderLayout.EAST);
        searchField.addActionListener(event -> applySearchNow());
        searchField.getDocument().addDocumentListener(new DocumentListener() {
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
        searchFieldSelector.addActionListener(event -> searchTimer.restart());
        availableOnly.addActionListener(event -> searchTimer.restart());
        showBorrowed.addActionListener(event -> searchTimer.restart());
        navigationAndSearch.add(searchPanel, BorderLayout.SOUTH);
        panel.add(navigationAndSearch, BorderLayout.NORTH);

        centerCards.add(catalogPanel, MenuSection.BOOKKEEPING.name());
        centerCards.add(contactCatalogPanel, "CONTACTS");
        centerCards.add(contactDetailPanel, "CONTACT_DETAILS");
        centerCards.add(buildNewLendPanel(), "NEW_LEND");
        centerCards.add(activeLendsPanel, "LEND_ACTIVE");
        centerCards.add(lendHistoryPanel, "LEND_HISTORY");
        centerCards.add(borrowBookPanel, "NEW_BORROW");
        centerCards.add(activeBorrowsPanel, "BORROW_ACTIVE");
        centerCards.add(borrowHistoryPanel, "BORROW_HISTORY");
        for (MenuSection section : MenuSection.values()) {
            if (section != MenuSection.BOOKKEEPING) {
                centerCards.add(placeholder(section.label() + " tools will be available in a later phase."),
                        section.name());
            }
        }
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
            rightCards.add(notificationPanels.get(notificationContext(section)), section.name());
        }
        panel.add(rightCards, BorderLayout.CENTER);
        return panel;
    }

    private void selectSection(MenuSection section) {
        currentSection = section;
        lendFlow = false;
        contactsSearchMode = false;
        ((CardLayout) leftCards.getLayout()).show(leftCards, section.name());
        ((CardLayout) centerCards.getLayout()).show(centerCards,
                section == MenuSection.BORROWING ? "NEW_BORROW" : section.name());
        ((CardLayout) rightCards.getLayout()).show(rightCards, section.name());
        JLabel heading = (JLabel) leftCards.getClientProperty("heading");
        heading.setText(section.label());
        configureSearch(false);
        menuButtons.forEach((menu, button) -> {
            boolean selected = menu == section;
            button.setBackground(selected ? Theme.ACCENT : Theme.WHITE);
            button.setForeground(selected ? Theme.WHITE : Theme.INK);
            button.setOpaque(true);
        });
        try {
            settingsService.save(Map.of("ui.lastMenu", section.name()));
        } catch (IOException exception) {
            logSettingsError(exception);
        }
        revalidate();
        repaint();
        NotificationContext context = notificationContext(section);
        notificationPanels.get(context).refresh();
        scanNotifications(context);
    }

    private void scanNotifications(NotificationContext context) {
        if (!notificationUiReady) {
            return;
        }
        if (context == null) {
            notificationScanAllPending = true;
        } else {
            pendingNotificationContexts.add(context);
        }
        startNextNotificationScan();
    }

    private void startNextNotificationScan() {
        if (notificationScanRunning) {
            return;
        }
        boolean scanAll = notificationScanAllPending;
        NotificationContext context = null;
        if (scanAll) {
            notificationScanAllPending = false;
            pendingNotificationContexts.clear();
        } else if (!pendingNotificationContexts.isEmpty()) {
            context = pendingNotificationContexts.iterator().next();
            pendingNotificationContexts.remove(context);
        } else {
            return;
        }
        NotificationContext scannedContext = context;
        notificationScanRunning = true;
        notificationWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                if (scanAll) {
                    alertScanner.scanAll();
                } else {
                    alertScanner.scan(scannedContext);
                }
                return null;
            }

            @Override
            protected void done() {
                notificationScanRunning = false;
                try {
                    get();
                    if (scanAll) {
                        notificationPanels.values().forEach(NotificationPanel::refresh);
                    } else {
                        notificationPanels.get(scannedContext).refresh();
                    }
                } catch (CancellationException exception) {
                    return;
                } catch (Exception exception) {
                    System.getLogger(MainFrame.class.getName()).log(System.Logger.Level.ERROR,
                            "Background notification scan failed", exception);
                }
                startNextNotificationScan();
            }
        };
        notificationWorker.execute();
    }

    private void stopNotificationTimer() {
        notificationTimer.stop();
        notificationScanAllPending = false;
        pendingNotificationContexts.clear();
        notificationService.removeChangeListener(notificationListener);
        notificationService.close();
        if (notificationWorker != null) {
            notificationWorker.cancel(true);
        }
    }

    public void shutdown() {
        searchTimer.stop();
        stopNotificationTimer();
    }

    private void notificationChanged(NotificationContext context) {
        SwingUtilities.invokeLater(() -> {
            NotificationPanel panel = notificationPanels.get(context);
            if (panel != null) {
                panel.refresh();
            }
        });
    }

    private void updateMenuBadge(NotificationContext context, int count) {
        MenuSection section = menuSection(context);
        JButton button = menuButtons.get(section);
        if (button != null) {
            button.setText(section.label() + (count == 0 ? "" : "  " + count));
            button.setToolTipText(count == 0 ? section.label() : section.label() + ": " + count + " alerts");
        }
    }

    private static NotificationContext notificationContext(MenuSection section) {
        return switch (section) {
            case BOOKKEEPING -> NotificationContext.BOOKKEEPING;
            case LENDING -> NotificationContext.LEND;
            case BORROWING -> NotificationContext.BORROW;
        };
    }

    private static MenuSection menuSection(NotificationContext context) {
        return switch (context) {
            case BOOKKEEPING -> MenuSection.BOOKKEEPING;
            case LEND -> MenuSection.LENDING;
            case BORROW -> MenuSection.BORROWING;
        };
    }

    private void restoreCatalogSort() {
        int column;
        try {
            column = Integer.parseInt(settingsService.getString("ui.books.sortColumn"));
        } catch (NumberFormatException exception) {
            column = 0;
        }
        String direction = settingsService.getString("ui.books.sortDirection");
        SortOrder order = "DESCENDING".equals(direction) ? SortOrder.DESCENDING : SortOrder.ASCENDING;
        catalogPanel.setSort(column >= 0 && column < 10 ? column : 0, order);
    }

    private void saveCatalogSort() {
        // ASSUMPTION: Keep the last menu and catalog sort per account between launches.
        SortOrder order = catalogPanel.sortOrder() == SortOrder.DESCENDING
                ? SortOrder.DESCENDING
                : SortOrder.ASCENDING;
        try {
            settingsService.save(Map.of("ui.books.sortColumn", Integer.toString(catalogPanel.sortColumn()),
                    "ui.books.sortDirection", order.name()));
        } catch (IOException exception) {
            logSettingsError(exception);
        }
    }

    private void refreshPricingIfChanged() {
        String currentSignature = pricingSettingsSignature();
        if (!currentSignature.equals(pricingSettingsSignature)) {
            pricingSettingsSignature = currentSignature;
            lendCartPanel.setPricingTerms(settingsService.pricingFor("lend"));
            borrowBookPanel.setPricingTerms(settingsService.pricingFor("borrow"));
        }
    }

    private String pricingSettingsSignature() {
        return settingsService.pricingDefaults().toField() + "|" + settingsService.pricingFor("lend").toField()
                + "|" + settingsService.pricingFor("borrow").toField();
    }

    private static void logSettingsError(Exception exception) {
        System.getLogger(MainFrame.class.getName()).log(System.Logger.Level.ERROR,
                "Settings preference could not be saved", exception);
    }

    private void applySearchNow() {
        searchTimer.stop();
        applySearch();
    }

    private void applySearch() {
        if (lendFlow) {
            try {
                BookSearchField field = BookSearchField.values()[searchFieldSelector.getSelectedIndex()];
                lendBookPickerPanel.setBooks(bookService.search(field, searchField.getText(), false));
            } catch (IOException exception) {
                logBookError(exception);
            }
            return;
        }
        if (contactsSearchMode) {
            applyContactSearch();
            return;
        }
        try {
            BookSearchField field = BookSearchField.values()[searchFieldSelector.getSelectedIndex()];
            catalogPanel.setBooks(bookService.search(field, searchField.getText(), availableOnly.isSelected(),
                    showBorrowed.isSelected()), bookService.listSorted(showBorrowed.isSelected()).size());
            updateBookActionButtons();
        } catch (IOException exception) {
            logBookError(exception);
            JOptionPane.showMessageDialog(this, "The catalog could not be searched.",
                    "Catalog error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel buildContactActions() {
        JPanel actions = new JPanel();
        actions.setBackground(Theme.PAPER);
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        for (String action : List.of("Add contact", "Update contact", "Delete contact", "View details", "Back")) {
            JButton button = new JButton(action);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setAlignmentX(LEFT_ALIGNMENT);
            button.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 42));
            button.addActionListener(event -> handleContactAction(action));
            if (!action.equals("Add contact") && !action.equals("Back")) {
                bookActionButtons.put("contact:" + action, button);
            }
            actions.add(button);
            actions.add(Box.createVerticalStrut(8));
        }
        return actions;
    }

    private JPanel buildNewLendPanel() {
        JPanel panel = new JPanel(new java.awt.GridLayout(2, 1, 0, Theme.GAP));
        panel.setBackground(Theme.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        panel.add(lendContactPicker);
        panel.add(lendBookPickerPanel);
        return panel;
    }

    private void openContactsManager(MenuSection sourceSection) {
        contactSourceSection = sourceSection;
        currentSection = sourceSection;
        contactsSearchMode = true;
        ((CardLayout) leftCards.getLayout()).show(leftCards, "CONTACT_ACTIONS");
        ((CardLayout) centerCards.getLayout()).show(centerCards, "CONTACTS");
        ((CardLayout) rightCards.getLayout()).show(rightCards, sourceSection.name());
        ((JLabel) leftCards.getClientProperty("heading")).setText("Contacts");
        configureSearch(true);
        refreshContactList();
        updateContactActionButtons();
    }

    private void showContactCatalog() {
        contactsSearchMode = true;
        ((CardLayout) centerCards.getLayout()).show(centerCards, "CONTACTS");
        refreshContactList();
    }

    private void showNewFlow(MenuSection section, String action) {
        if (section == MenuSection.LENDING) {
            beginNewLend();
            return;
        }
        contactsSearchMode = false;
        ((CardLayout) centerCards.getLayout()).show(centerCards,
                action.equals("New lend") ? "NEW_LEND" : "NEW_BORROW");
        configureSearch(false);
    }

    private void beginNewLend() {
        lendFlow = true;
        contactsSearchMode = false;
        ((CardLayout) leftCards.getLayout()).show(leftCards, "LEND_CART");
        ((CardLayout) centerCards.getLayout()).show(centerCards, "NEW_LEND");
        ((CardLayout) rightCards.getLayout()).show(rightCards, MenuSection.LENDING.name());
        ((JLabel) leftCards.getClientProperty("heading")).setText("New lend");
        configureSearch(false);
        applySearchNow();
    }

    private void addToLendCart(Book book) {
        if (lendCartPanel.addBook(book)) {
            ((CardLayout) leftCards.getLayout()).show(leftCards, "LEND_CART");
        }
    }

    private void confirmLend() {
        Optional<Contact> contact = lendContactPicker.getSelectedContact();
        if (contact.isEmpty()) {
            lendCartPanel.setStatus("Choose a contact before confirming.");
            return;
        }
        try {
            List<LendingService.LendRequest> requests = lendCartPanel.requests();
            model.PricingTerms terms = lendCartPanel.pricingTerms();
            String summary = "Lend to: " + contact.get().name() + "\n\n"
                    + requests.stream().map(request -> "- " + bookTitle(request.bookId()) + "  due "
                            + request.dueDate()).collect(java.util.stream.Collectors.joining("\n"))
                    + "\n\nPricing:\n" + pricingSummary(terms);
            if (JOptionPane.showConfirmDialog(this, summary, "Confirm lend", JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE) != JOptionPane.OK_OPTION) {
                return;
            }
            int lentCount = requests.size();
            lendingService.lend(contact.get().contactId(), requests, terms);
            lendCartPanel.clear();
            lendCartPanel.setStatus("Lent " + lentCount + " book" + (lentCount == 1 ? "." : "s."));
            applySearchNow();
            refreshLendViews();
        } catch (LendingValidationException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Lend could not be confirmed",
                    JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException exception) {
            lendCartPanel.setStatus(exception.getMessage());
        } catch (IOException exception) {
            logLoanError(exception);
            JOptionPane.showMessageDialog(this, "The lend could not be saved. Check local storage and try again.",
                    "Lending error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private String bookTitle(String bookId) {
        try {
            return bookService.getById(bookId).map(Book::title).orElse("Removed book");
        } catch (IOException exception) {
            return "Book";
        }
    }

    private static String pricingSummary(model.PricingTerms terms) {
        String base = terms.baseEnabled() ? DisplayFormat.money(terms.baseAmount()) + " per book" : "Off";
        String rent = terms.rentEnabled()
                ? DisplayFormat.money(terms.rentAmount()) + " per " + terms.rentUnit().name().toLowerCase()
                : "Off";
        String penalty = terms.penaltyEnabled()
                ? DisplayFormat.money(terms.penaltyPerDay()) + " per overdue day"
                : "Off";
        return "Base: " + base + "\nRent: " + rent + "\nOverdue penalty: " + penalty;
    }

    private void showLendList(String cardName, LoanListPanel panel) {
        showLoanList(cardName, panel, MenuSection.LENDING);
    }

    private void openReturnDialog(String loanId) {
        openReturnDialog(loanId, LoanDirection.LENT);
    }

    private void openReturnDialog(String loanId, LoanDirection direction) {
        ReceiveReturnDialog dialog = new ReceiveReturnDialog(SwingUtilities.getWindowAncestor(this), contactService,
                bookService, lendingService, () -> {
                    refreshLendViews();
                    refreshBorrowViews();
                    applySearchNow();
                    JOptionPane.showMessageDialog(this,
                            direction == LoanDirection.BORROWED ? "Book returned to lender and archived."
                                    : "Return recorded.",
                            "Return",
                            JOptionPane.INFORMATION_MESSAGE);
                }, loanId, direction);
        dialog.setVisible(true);
    }

    private void showLoanList(String cardName, LoanListPanel panel, MenuSection section) {
        lendFlow = false;
        contactsSearchMode = false;
        panel.refresh();
        ((CardLayout) centerCards.getLayout()).show(centerCards, cardName);
        ((CardLayout) leftCards.getLayout()).show(leftCards, section.name());
        ((CardLayout) rightCards.getLayout()).show(rightCards, section.name());
        ((JLabel) leftCards.getClientProperty("heading")).setText(section.label());
        configureSearch(false);
    }

    private void markAsPaid(model.Loan loan) {
        try {
            lendingService.markAsPaid(loan.loanId());
            refreshLendViews();
            refreshBorrowViews();
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Payment", JOptionPane.ERROR_MESSAGE);
        } catch (IOException exception) {
            logLoanError(exception);
            JOptionPane.showMessageDialog(this, "Payment status could not be saved.", "Payment",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshLendViews() {
        activeLendsPanel.refresh();
        lendHistoryPanel.refresh();
    }

    private void refreshBorrowViews() {
        activeBorrowsPanel.refresh();
        borrowHistoryPanel.refresh();
    }

    private String contactName(String contactId) {
        try {
            return contactService.getById(contactId).map(Contact::name).orElse("Unknown contact");
        } catch (IOException exception) {
            logContactError(exception);
            return "Unknown contact";
        }
    }

    private static void logLoanError(Exception exception) {
        System.getLogger(MainFrame.class.getName()).log(System.Logger.Level.ERROR,
                "Lending operation failed", exception);
    }

    private void handleContactAction(String action) {
        Optional<Contact> selectedContact = contactCatalogPanel.selectedContact();
        switch (action) {
            case "Add contact" -> openContactForm(null);
            case "Update contact" -> selectedContact.ifPresent(this::openContactForm);
            case "Delete contact" -> selectedContact.ifPresent(this::confirmDeleteContact);
            case "View details" -> selectedContact.ifPresent(this::showContactDetails);
            case "Back" -> backToMenu();
            default -> throw new IllegalArgumentException("Unknown contact action: " + action);
        }
    }

    private void openContactForm(Contact existing) {
        ContactFormDialog dialog = new ContactFormDialog(SwingUtilities.getWindowAncestor(this), contactService,
                existing);
        if (dialog.showDialog().isPresent()) {
            refreshContactList();
        }
    }

    private void confirmDeleteContact(Contact contact) {
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete \"" + contact.name() + "\" from your contacts? This cannot be undone.",
                "Delete contact", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            contactService.delete(contact.contactId());
            refreshContactList();
        } catch (IllegalArgumentException exception) {
            showContactValidationError(exception);
        } catch (IOException exception) {
            logContactError(exception);
            JOptionPane.showMessageDialog(this, "The contact could not be deleted. Check local storage and try again.",
                    "Contacts", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showContactDetails(Contact contact) {
        contactDetailPanel.setContact(contact);
        try {
            List<model.Loan> loans = lendingService.loansByContact(contact.contactId()).stream()
                    .toList();
            List<model.Loan> lends = loans.stream()
                    .filter(loan -> loan.direction() == LoanDirection.LENT).toList();
            List<model.Loan> borrows = loans.stream()
                    .filter(loan -> loan.direction() == LoanDirection.BORROWED).toList();
            List<List<String>> lendRows = lends.stream().map(loan -> List.of(loan.bookTitleSnapshot(),
                    loan.status().name(), loan.issueDate().toString(), loan.dueDate().toString(),
                    loan.returnDate() == null ? "" : loan.returnDate().toString(),
                    Long.toString(loan.status() == model.LoanStatus.ACTIVE
                            ? services.LoanRules.daysOverdue(loan.dueDate(), java.time.LocalDate.now())
                            : 0),
                    loan.paymentStatus().name())).toList();
            List<List<String>> borrowRows = borrows.stream().map(loan -> List.of(loan.bookTitleSnapshot(),
                    loan.status().name(), loan.issueDate().toString(), loan.dueDate().toString(),
                    loan.returnDate() == null ? "" : loan.returnDate().toString(),
                    Long.toString(loan.status() == model.LoanStatus.ACTIVE
                            ? services.LoanRules.daysOverdue(loan.dueDate(), java.time.LocalDate.now())
                            : 0),
                    loan.paymentStatus().name())).toList();
            java.math.BigDecimal owedToOwner = amountOwed(lends);
            java.math.BigDecimal owedToContact = amountOwed(borrows);
            contactDetailPanel.setLoanData(lendRows, borrowRows, owedToOwner, owedToContact);
        } catch (IOException exception) {
            logLoanError(exception);
            JOptionPane.showMessageDialog(this, "Loan details could not be loaded.", "Contact details",
                    JOptionPane.ERROR_MESSAGE);
        }
        ((CardLayout) centerCards.getLayout()).show(centerCards, "CONTACT_DETAILS");
    }

    private void openNotification(Notification notification) {
        switch (notification.type()) {
            case BOOK_ADDED, BOOK_UPDATED, BOOK_REMOVED -> openBookNotification(notification);
            case AMOUNT_OWED_TO_YOU, AMOUNT_YOU_OWE -> openContactNotification(notification);
            default -> openLoanNotification(notification);
        }
    }

    private void openBookNotification(Notification notification) {
        try {
            Optional<Book> book = bookService.getById(notification.refId());
            if (book.isEmpty()) {
                JOptionPane.showMessageDialog(this, "This book is no longer in the catalog.",
                        "Notification", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            selectSection(MenuSection.BOOKKEEPING);
            showBorrowed.setSelected(book.get().archived());
            searchField.setText("");
            applySearchNow();
            catalogPanel.selectBook(notification.refId());
        } catch (IOException exception) {
            logBookError(exception);
        }
    }

    private void openLoanNotification(Notification notification) {
        try {
            Optional<model.Loan> loan = lendingService.findLoan(notification.refId());
            if (loan.isEmpty()) {
                JOptionPane.showMessageDialog(this, "This loan is no longer available.",
                        "Notification", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            MenuSection section = loan.get().direction() == LoanDirection.LENT
                    ? MenuSection.LENDING
                    : MenuSection.BORROWING;
            boolean active = loan.get().status() == model.LoanStatus.ACTIVE;
            LoanListPanel panel = loan.get().direction() == LoanDirection.LENT
                    ? active ? activeLendsPanel : lendHistoryPanel
                    : active ? activeBorrowsPanel : borrowHistoryPanel;
            String card = loan.get().direction() == LoanDirection.LENT
                    ? active ? "LEND_ACTIVE" : "LEND_HISTORY"
                    : active ? "BORROW_ACTIVE" : "BORROW_HISTORY";
            selectSection(section);
            showLoanList(card, panel, section);
            panel.selectLoan(notification.refId());
        } catch (IOException exception) {
            logLoanError(exception);
        }
    }

    private void openContactNotification(Notification notification) {
        try {
            Optional<Contact> contact = contactService.getById(notification.refId());
            if (contact.isEmpty()) {
                JOptionPane.showMessageDialog(this, "This contact is no longer available.",
                        "Notification", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            MenuSection source = menuSection(notification.context());
            openContactsManager(source);
            showContactDetails(contact.get());
        } catch (IOException exception) {
            logContactError(exception);
        }
    }

    private static java.math.BigDecimal amountOwed(List<model.Loan> loans) {
        return loans.stream().filter(loan -> loan.paymentStatus() == model.PaymentStatus.OWED)
                .map(loan -> loan.baseCharge()
                        .add(loan.rentCharge() == null ? java.math.BigDecimal.ZERO : loan.rentCharge())
                        .add(loan.overduePenalty() == null ? java.math.BigDecimal.ZERO : loan.overduePenalty())
                        .add(loan.damageCharge() == null ? java.math.BigDecimal.ZERO : loan.damageCharge()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    private void backToMenu() {
        contactsSearchMode = false;
        ((CardLayout) leftCards.getLayout()).show(leftCards, contactSourceSection.name());
        ((CardLayout) centerCards.getLayout()).show(centerCards,
                contactSourceSection == MenuSection.BORROWING ? "NEW_BORROW" : contactSourceSection.name());
        ((CardLayout) rightCards.getLayout()).show(rightCards, contactSourceSection.name());
        ((JLabel) leftCards.getClientProperty("heading")).setText(contactSourceSection.label());
        menuButtons.forEach((menu, button) -> {
            boolean selected = menu == contactSourceSection;
            button.setBackground(selected ? Theme.ACCENT : Theme.WHITE);
            button.setForeground(selected ? Theme.WHITE : Theme.INK);
            button.setOpaque(true);
        });
        configureSearch(false);
        applySearchNow();
    }

    private void configureSearch(boolean contactsMode) {
        contactsSearchMode = contactsMode;
        searchTimer.stop();
        searchField.setText("");
        searchPanel.setVisible(contactsMode || currentSection == MenuSection.BOOKKEEPING || lendFlow);
        searchFieldSelector.removeAllItems();
        if (contactsMode) {
            for (String field : List.of("All", "Name", "Phone", "Email", "Notes")) {
                searchFieldSelector.addItem(field);
            }
            searchField.setName("contactSearch");
            searchField.getAccessibleContext().setAccessibleName("Search contacts");
            availableOnly.setVisible(false);
            showBorrowed.setVisible(false);
        } else {
            for (String field : List.of("All", "Title", "Author", "ISBN", "Publisher", "Category", "Language",
                    "Year", "Reading status")) {
                searchFieldSelector.addItem(field);
            }
            searchField.setName("catalogSearch");
            searchField.getAccessibleContext().setAccessibleName("Search books");
            availableOnly.setVisible(true);
            showBorrowed.setVisible(currentSection == MenuSection.BOOKKEEPING && !lendFlow);
        }
        searchTimer.restart();
    }

    private void applyContactSearch() {
        try {
            ContactSearchField field = ContactSearchField.values()[searchFieldSelector.getSelectedIndex()];
            contactCatalogPanel.setContacts(contactService.search(field, searchField.getText()),
                    contactService.listSorted().size());
            updateContactActionButtons();
        } catch (IOException exception) {
            logContactError(exception);
            JOptionPane.showMessageDialog(this, "Contacts could not be searched.",
                    "Contacts", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshContactList() {
        try {
            ContactSearchField field = ContactSearchField.values()[searchFieldSelector.getSelectedIndex()];
            contactCatalogPanel.setContacts(contactService.search(field, searchField.getText()),
                    contactService.listSorted().size());
            updateContactActionButtons();
        } catch (IOException exception) {
            logContactError(exception);
            JOptionPane.showMessageDialog(this, "Contacts could not be loaded.",
                    "Contacts", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateContactActionButtons() {
        boolean selected = contactCatalogPanel.selectedContact().isPresent();
        for (String action : List.of("Update contact", "Delete contact", "View details")) {
            JButton button = bookActionButtons.get("contact:" + action);
            if (button != null) {
                button.setEnabled(selected);
                button.setToolTipText(selected ? action : "Select a contact first");
            }
        }
    }

    private void showContactValidationError(IllegalArgumentException exception) {
        JOptionPane.showMessageDialog(this, exception.getMessage(), "Contacts", JOptionPane.ERROR_MESSAGE);
    }

    private static void logContactError(Exception exception) {
        System.getLogger(MainFrame.class.getName()).log(System.Logger.Level.ERROR,
                "Contact operation failed", exception);
    }

    private void handleBookAction(String action) {
        Optional<Book> selectedBook = catalogPanel.selectedBook();
        switch (action) {
            case "Add book" -> openBookForm(null);
            case "Update book" -> selectedBook.ifPresent(this::openBookForm);
            case "Remove book" -> selectedBook.ifPresent(this::confirmRemoveBook);
            case "View details" -> selectedBook.ifPresent(this::showBookDetails);
            default -> throw new IllegalArgumentException("Unknown book action: " + action);
        }
    }

    private void openBookForm(Book existing) {
        BookFormDialog dialog = new BookFormDialog(SwingUtilities.getWindowAncestor(this), bookService, existing,
                this::showBookDetails);
        if (dialog.showDialog().isPresent()) {
            applySearchNow();
        }
    }

    private void confirmRemoveBook(Book book) {
        int choice = JOptionPane.showConfirmDialog(this,
                "Remove \"" + book.title() + "\" from your catalog? This cannot be undone.",
                "Remove book", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            bookService.remove(book.bookId());
            applySearchNow();
        } catch (IllegalArgumentException exception) {
            showValidationError(exception);
        } catch (IOException exception) {
            logBookError(exception);
            JOptionPane.showMessageDialog(this, "The book could not be removed. Check local storage and try again.",
                    "Catalog error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showBookDetails(Book book) {
        String details = "Title: " + book.title() + "\nAuthors: " + book.authors() + "\nISBN: " + book.isbn()
                + "\nPublisher: " + book.publisher() + "\nYear: " + book.publicationYear()
                + "\nEdition: " + book.edition() + "\nCategory: " + book.category() + "\nLanguage: "
                + book.language() + "\nPrice: " + DisplayFormat.money(book.price())
                + "\nOwnership: " + (book.ownershipType() == model.Book.OwnershipType.BORROWED
                        ? "Borrowed from " + contactName(book.lenderContactId())
                        : "Owned")
                + "\nCopies: " + (book.ownershipType() == model.Book.OwnershipType.BORROWED ? "1 borrowed"
                        : book.availableCopies() + " available / " + book.totalCopies() + " owned")
                + "\nCondition: " + titleCase(book.condition().name()) + "\nReading status: "
                + titleCase(book.readingStatus().name()) + "\nNotes: " + book.notes() + "\nDate added: "
                + DisplayFormat.date(book.dateAdded());
        JOptionPane.showMessageDialog(this, details, "Book details", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateBookActionButtons() {
        boolean selected = catalogPanel.selectedBook().isPresent();
        for (String action : java.util.List.of("Update book", "Remove book", "View details")) {
            JButton button = bookActionButtons.get(action);
            if (button != null) {
                button.setEnabled(selected);
                button.setToolTipText(selected ? action : "Select a book first");
            }
        }
    }

    private void showValidationError(IllegalArgumentException exception) {
        JOptionPane.showMessageDialog(this, exception.getMessage(), "Catalog", JOptionPane.ERROR_MESSAGE);
    }

    private static String titleCase(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    private static void logBookError(Exception exception) {
        System.getLogger(MainFrame.class.getName()).log(System.Logger.Level.ERROR,
                "Book catalog operation failed", exception);
    }

    private static JPanel placeholder(String message) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Theme.WHITE);
        panel.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setForeground(Theme.MUTED);
        panel.add(label, BorderLayout.CENTER);
        return panel;
    }

    private static JPanel sectionPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(Theme.PAPER);
        return panel;
    }
}
