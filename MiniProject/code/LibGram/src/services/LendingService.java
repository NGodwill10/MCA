package services;

import data.BookRepository;
import data.LoanRepository;
import data.MemberRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import model.Book;
import model.Loan;
import model.LoanStatus;
import model.Member;
import model.MemberStatus;
import model.User;

/** Validates and records lending transactions. */
public final class LendingService {
    private final BookRepository books;
    private final MemberRepository members;
    private final LoanRepository loans;
    private final SettingsService settings;
    private final DuesProvider dues;
    private final PermissionPolicy permissions;
    private final LoanEventListener events;
    private final ReservationService reservations;
    private final ChargeService charges;
    private int repairedInventoryCount;

    public LendingService(BookRepository books, MemberRepository members, LoanRepository loans,
            SettingsService settings) throws IOException {
        this(books, members, loans, settings, new NoDuesProvider(), new PermissionPolicy(), LoanEventListener.NO_OP);
    }

    public LendingService(BookRepository books, MemberRepository members, LoanRepository loans,
            SettingsService settings, DuesProvider dues, PermissionPolicy permissions,
            LoanEventListener events) throws IOException {
        this(books, members, loans, settings, dues, permissions, events, null, null);
    }

    public LendingService(BookRepository books, MemberRepository members, LoanRepository loans,
            SettingsService settings, DuesProvider dues, PermissionPolicy permissions,
            LoanEventListener events, ReservationService reservations) throws IOException {
        this(books, members, loans, settings, dues, permissions, events, reservations, null);
    }

    public LendingService(BookRepository books, MemberRepository members, LoanRepository loans,
            SettingsService settings, DuesProvider dues, PermissionPolicy permissions,
            LoanEventListener events, ReservationService reservations, ChargeService charges) throws IOException {
        this.books = Objects.requireNonNull(books, "books");
        this.members = Objects.requireNonNull(members, "members");
        this.loans = Objects.requireNonNull(loans, "loans");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.dues = Objects.requireNonNull(dues, "dues");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.events = Objects.requireNonNull(events, "events");
        this.reservations = reservations;
        this.charges = charges;
        repairInventory();
    }

    public List<Loan> lend(User actor, String memberId, List<LendingRequest> requests) throws IOException {
        requireCirculation(actor);
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("Add at least one book to the lending list.");
        }
        Member member = members.findAll().stream().filter(item -> item.memberId().equals(memberId)).findFirst()
                .orElse(null);
        List<String> failures = new ArrayList<>();
        LocalDate today = LocalDate.now();
        if (member == null)
            failures.add("Member does not exist.");
        else {
            if (member.status() != MemberStatus.ACTIVE)
                failures.add("Member is not Active.");
            if (member.expiredOn(today))
                failures.add("Membership expired on " + member.expiryDate() + ".");
            List<Loan> memberActive = activeLoansForMember(memberId);
            List<Loan> overdue = memberActive.stream().filter(loan -> loan.dueDate().isBefore(today)).toList();
            if (!overdue.isEmpty())
                failures.add("" + overdue.size() + " overdue book(s) must be returned first: "
                        + overdue.stream().map(Loan::bookTitleSnapshot).toList());
            BigDecimal balance = dues.unpaidBalance(memberId);
            if (balance != null && balance.signum() > 0)
                failures.add("Member has unpaid dues of Rs. " + balance + ".");
            int limit = settings.getInt(SettingsDefaults.key("limit.", member.membershipType()));
            if (memberActive.size() + requests.size() > limit) {
                failures.add("Borrowing limit " + limit + " exceeded: " + memberActive.size() + " already borrowed + "
                        + requests.size() + " requested.");
            }
        }

        Set<String> requestedBooks = new HashSet<>();
        Map<String, Book> bookById = new HashMap<>();
        for (LendingRequest request : requests) {
            Book book = books.findAll().stream().filter(item -> item.bookId().equals(request.bookId())).findFirst()
                    .orElse(null);
            if (book == null) {
                failures.add("Book " + request.bookId() + " does not exist.");
                continue;
            }
            bookById.put(book.bookId(), book);
            if (!requestedBooks.add(book.bookId()))
                failures.add("The book is listed more than once: " + book.title() + ".");
            boolean reservedForMember = reservations != null && reservations.readyFor(memberId, book.bookId());
            int lendable = reservations == null ? book.availableCopies() : reservations.lendableCopies(book.bookId());
            if (!reservedForMember && lendable < 1) {
                boolean held = reservations != null && reservations.readyCount(book.bookId()) > 0;
                failures.add(
                        held ? "A copy is held for another member." : "No copies available for: " + book.title() + ".");
            }
            if (activeLoansForMemberAndBook(memberId, book.bookId()).size() > 0) {
                failures.add("Member already holds an active loan for: " + book.title() + ".");
            }
            if (request.dueDate() == null || request.dueDate().isBefore(today)
                    || request.dueDate().isAfter(today.plusDays(settings.getInt("loan.maxDays")))) {
                failures.add("Due date for " + book.title() + " must be between today and the maximum loan date.");
            }
        }
        if (!failures.isEmpty()) {
            events.onLendingBlocked(memberId, String.join("; ", failures));
            throw new IllegalArgumentException(String.join("\n", failures));
        }

        List<Loan> oldLoans = loans.findAll();
        List<Book> oldBooks = books.findAll();
        List<model.Charge> oldCharges = charges == null ? List.of() : charges.allRecords();
        List<Loan> newLoans = new ArrayList<>(oldLoans);
        List<Book> updatedBooks = new ArrayList<>(oldBooks);
        List<Loan> created = new ArrayList<>();
        List<model.Charge> newCharges = new ArrayList<>(oldCharges);
        for (LendingRequest request : requests) {
            Book book = bookById.get(request.bookId());
            BigDecimal base = settings.getBoolean("charge.base.enabled") ? settings.getMoney("charge.base.amount")
                    : moneyZero();
            Loan loan = new Loan(UUID.randomUUID().toString(), book.bookId(), book.title(), memberId, today,
                    request.dueDate(), null, LoanStatus.ACTIVE, base, moneyZero(), moneyZero(), "", actor.username(),
                    "",
                    pricingSnapshot());
            created.add(loan);
            newLoans.add(loan);
            if (charges != null && base.signum() > 0) {
                newCharges.add(charges.create(memberId, loan.loanId(), model.ChargeType.BASE,
                        "Base charge", base));
            }
            int index = indexOfBook(updatedBooks, book.bookId());
            updatedBooks.set(index, withAvailable(book, book.availableCopies() - 1));
        }
        try {
            loans.saveAll(newLoans);
            books.saveAll(updatedBooks);
            if (charges != null)
                charges.replaceAll(newCharges);
        } catch (IOException exception) {
            try {
                loans.saveAll(oldLoans);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            try {
                books.saveAll(oldBooks);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            if (charges != null) {
                try {
                    charges.replaceAll(oldCharges);
                } catch (IOException rollback) {
                    exception.addSuppressed(rollback);
                }
            }
            throw exception;
        } catch (RuntimeException exception) {
            try {
                loans.saveAll(oldLoans);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            try {
                books.saveAll(oldBooks);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            if (charges != null) {
                try {
                    charges.replaceAll(oldCharges);
                } catch (IOException rollback) {
                    exception.addSuppressed(rollback);
                }
            }
            throw exception;
        }
        created.forEach(loan -> events.onLoanCreated(loan));
        if (reservations != null)
            for (LendingRequest request : requests)
                reservations.markCollected(memberId, request.bookId());
        return List.copyOf(created);
    }

    public List<Loan> lend(User actor, Member member, List<LendingRequest> requests) throws IOException {
        return lend(actor, member.memberId(), requests);
    }

    public List<Loan> activeLoans() {
        return loans.findAll().stream().filter(LendingService::active).sorted(dueOrder()).toList();
    }

    public List<Loan> activeLoansForMember(String memberId) {
        return activeLoans().stream().filter(l -> l.memberId().equals(memberId)).toList();
    }

    public List<Loan> loansForMember(String memberId) {
        return loans.findAll().stream().filter(l -> l.memberId().equals(memberId))
                .sorted(Comparator.comparing(Loan::issueDate).reversed()).toList();
    }

    public List<Loan> loansForBook(String bookId) {
        return loans.findAll().stream().filter(l -> l.bookId().equals(bookId)).toList();
    }

    public List<Loan> overdueLoans() {
        return activeLoans().stream().filter(l -> l.dueDate().isBefore(LocalDate.now())).toList();
    }

    public int repairedInventoryCount() {
        return repairedInventoryCount;
    }

    public int consumeInventoryRepairCount() {
        int count = repairedInventoryCount;
        repairedInventoryCount = 0;
        return count;
    }

    public int borrowingLimit(model.Member member) {
        return settings.getInt(SettingsDefaults.key("limit.", member.membershipType()));
    }

    public LocalDate defaultDueDate() {
        return LocalDate.now().plusDays(settings.getInt("loan.defaultDays"));
    }

    public int lendableCopies(String bookId) {
        try {
            return reservations == null
                    ? books.findAll().stream().filter(b -> b.bookId().equals(bookId)).mapToInt(Book::availableCopies)
                            .findFirst().orElse(0)
                    : reservations.lendableCopies(bookId);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read book availability.", exception);
        }
    }

    private List<Loan> activeLoansForMemberAndBook(String memberId, String bookId) {
        return activeLoans().stream().filter(l -> l.memberId().equals(memberId) && l.bookId().equals(bookId)).toList();
    }

    private void repairInventory() throws IOException {
        List<Book> current = books.findAll();
        List<Book> repaired = new ArrayList<>(current);
        for (int i = 0; i < current.size(); i++) {
            Book book = current.get(i);
            long active = loans.findAll().stream().filter(l -> l.bookId().equals(book.bookId()) && active(l)).count();
            int expected = book.totalCopies() - (int) active;
            if (expected != book.availableCopies()) {
                repaired.set(i, withAvailable(book, Math.max(0, expected)));
                repairedInventoryCount++;
            }
        }
        if (repairedInventoryCount > 0)
            books.saveAll(repaired);
        if (repairedInventoryCount > 0) {
            System.getLogger(LendingService.class.getName()).log(System.Logger.Level.WARNING,
                    "Inventory counts for {0} books were corrected from active loans.", repairedInventoryCount);
        }
    }

    private String pricingSnapshot() {
        BigDecimal rent = settings.getBoolean("charge.rent.enabled") ? settings.getMoney("charge.rent.amount")
                : moneyZero();
        BigDecimal fine = settings.getBoolean("charge.penalty.enabled") ? settings.getMoney("charge.penalty.perDay")
                : moneyZero();
        return "base=" + settings.getMoney("charge.base.amount") + ";rent="
                + settings.getEnum("charge.rent.unit", RentUnit.class)
                + ":" + rent + ";penalty=" + fine + ":" + settings.getMoney("charge.penalty.maxPerItem");
    }

    private static Book withAvailable(Book book, int available) {
        return new Book(book.bookId(), book.isbn(), book.title(), book.authors(), book.publisher(),
                book.publicationYear(),
                book.edition(), book.category(), book.language(), book.shelfLocation(), book.price(),
                book.totalCopies(), available,
                book.dateAdded());
    }

    private static int indexOfBook(List<Book> records, String bookId) {
        for (int i = 0; i < records.size(); i++)
            if (records.get(i).bookId().equals(bookId))
                return i;
        throw new IllegalArgumentException("Book disappeared while lending.");
    }

    private static boolean active(Loan loan) {
        return loan.status() == LoanStatus.ACTIVE;
    }

    private static Comparator<Loan> dueOrder() {
        return Comparator.comparing(Loan::dueDate).thenComparing(Loan::loanId);
    }

    private static BigDecimal moneyZero() {
        return BigDecimal.ZERO.setScale(2);
    }

    private void requireCirculation(User actor) {
        if (actor == null || !actor.active() || !permissions.canCirculate(actor))
            throw new SecurityException("Only an active staff or administrator can lend books.");
    }

    public record LendingRequest(String bookId, java.time.LocalDate dueDate) {
    }

    private enum RentUnit {
        DAY, WEEK, MONTH
    }
}
