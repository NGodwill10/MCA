package services;

import data.BookRepository;
import data.LoanRepository;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import model.Book;
import model.Charge;
import model.Loan;
import model.LoanStatus;
import model.User;

public final class LostBookService {
    private final BookRepository books;
    private final LoanRepository loans;
    private final ChargeService charges;
    private final SettingsService settings;
    private final PermissionPolicy permissions;
    private final LostBookEventListener events;

    public LostBookService(BookRepository books, LoanRepository loans, ChargeService charges,
            SettingsService settings) {
        this(books, loans, charges, settings, LostBookEventListener.NO_OP);
    }

    public LostBookService(BookRepository books, LoanRepository loans, ChargeService charges,
            SettingsService settings, LostBookEventListener events) {
        this.books = books;
        this.loans = loans;
        this.charges = charges;
        this.settings = settings;
        this.permissions = new PermissionPolicy();
        this.events = events;
    }

    public Loan markLost(User actor, String loanId) throws IOException {
        require(actor);
        Loan loan = loans.findAll().stream().filter(item -> item.loanId().equals(loanId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Loan does not exist."));
        if (loan.status() != LoanStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an active loan can be marked lost.");
        }
        return markLost(loan, actor.username());
    }

    /** Marks loans lost when they reach the configured overdue threshold. */
    public int markOverdueLost(User actor) throws IOException {
        require(actor);
        return markOverdueLostAs("system");
    }

    /** Runs the startup maintenance pass without requiring an interactive actor. */
    public int markOverdueLost() throws IOException {
        return markOverdueLostAs("system");
    }

    private int markOverdueLostAs(String receivedBy) throws IOException {
        LocalDate today = LocalDate.now();
        int count = 0;
        for (Loan loan : new ArrayList<>(loans.findAll())) {
            if (loan.status() == LoanStatus.ACTIVE
                    && !today.isBefore(loan.dueDate().plusDays(settings.getInt("lost.afterDays")))) {
                markLost(loan, receivedBy);
                count++;
            }
        }
        return count;
    }

    private Loan markLost(Loan loan, String receivedBy) throws IOException {
        LocalDate lostOn = LocalDate.now();
        List<Loan> oldLoans = loans.findAll();
        List<Book> oldBooks = books.findAll();
        List<Charge> oldCharges = charges.allRecords();
        int loanIndex = indexLoan(oldLoans, loan.loanId());
        int bookIndex = indexBook(oldBooks, loan.bookId());
        Book book = oldBooks.get(bookIndex);
        Loan lost = new Loan(loan.loanId(), loan.bookId(), loan.bookTitleSnapshot(), loan.memberId(),
                loan.issueDate(), loan.dueDate(), lostOn, LoanStatus.LOST, loan.baseCharge(), loan.rentCharge(),
                ChargeCalculator.returnCharges(loan, book, lostOn, model.ReturnCondition.GOOD,
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO).stream()
                        .filter(line -> line.type() == model.ChargeType.OVERDUE_FINE)
                        .map(ChargeCalculator.Line::amount).findFirst().orElse(java.math.BigDecimal.ZERO),
                "", loan.issuedBy(), receivedBy, loan.pricingSnapshot());

        List<Loan> nextLoans = new ArrayList<>(oldLoans);
        nextLoans.set(loanIndex, lost);
        List<Book> nextBooks = new ArrayList<>(oldBooks);
        nextBooks.set(bookIndex, withInventory(book, Math.max(0, book.totalCopies() - 1), book.availableCopies()));
        List<Charge> nextCharges = new ArrayList<>(oldCharges);
        nextCharges.addAll(charges.createAll(loan.memberId(), loan.loanId(), ChargeCalculator.lostCharges(loan, book,
                lostOn, settings.getMoney("fee.lostProcessing"))));

        try {
            loans.saveAll(nextLoans);
            books.saveAll(nextBooks);
            charges.replaceAll(nextCharges);
        } catch (IOException | RuntimeException exception) {
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
            try {
                charges.replaceAll(oldCharges);
            } catch (IOException rollback) {
                exception.addSuppressed(rollback);
            }
            throw exception;
        }
        events.onBookLost(lost);
        return lost;
    }

    private void require(User actor) {
        if (actor == null || !actor.active() || !permissions.canCirculate(actor)) {
            throw new SecurityException("Only staff or an administrator can mark books lost.");
        }
    }

    private static int indexLoan(List<Loan> records, String id) {
        for (int i = 0; i < records.size(); i++)
            if (records.get(i).loanId().equals(id))
                return i;
        throw new IllegalArgumentException("Loan does not exist.");
    }

    private static int indexBook(List<Book> records, String id) {
        for (int i = 0; i < records.size(); i++)
            if (records.get(i).bookId().equals(id))
                return i;
        throw new IllegalArgumentException("Book does not exist.");
    }

    private static Book withInventory(Book book, int total, int available) {
        return new Book(book.bookId(), book.isbn(), book.title(), book.authors(), book.publisher(),
                book.publicationYear(), book.edition(), book.category(), book.language(), book.shelfLocation(),
                book.price(), total, available, book.dateAdded());
    }
}
