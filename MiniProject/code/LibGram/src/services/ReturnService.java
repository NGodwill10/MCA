package services;

import data.BookRepository;
import data.LoanRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import model.Book;
import model.Charge;
import model.ChargeType;
import model.Loan;
import model.LoanStatus;
import model.ReturnCondition;
import model.User;

/** Closes loans, creates return charges, and applies inventory changes. */
public final class ReturnService {
        private final BookRepository books;
        private final LoanRepository loans;
        private final ChargeService charges;
        private final SettingsService settings;
        private final PermissionPolicy permissions;
        private final ReservationService reservations;
        private final ReturnEventListener events;

        public ReturnService(BookRepository books, LoanRepository loans, ChargeService charges,
                        SettingsService settings) {
                this(books, loans, charges, settings, new PermissionPolicy(), null, ReturnEventListener.NO_OP);
        }

        public ReturnService(BookRepository books, LoanRepository loans, ChargeService charges,
                        SettingsService settings, PermissionPolicy permissions) {
                this(books, loans, charges, settings, permissions, null, ReturnEventListener.NO_OP);
        }

        public ReturnService(BookRepository books, LoanRepository loans, ChargeService charges,
                        SettingsService settings, PermissionPolicy permissions, ReservationService reservations) {
                this(books, loans, charges, settings, permissions, reservations, ReturnEventListener.NO_OP);
        }

        public ReturnService(BookRepository books, LoanRepository loans, ChargeService charges,
                        SettingsService settings, PermissionPolicy permissions, ReservationService reservations,
                        ReturnEventListener events) {
                this.books = books;
                this.loans = loans;
                this.charges = charges;
                this.settings = settings;
                this.permissions = permissions;
                this.reservations = reservations;
                this.events = events;
        }

        public List<ChargeCalculator.Line> preview(Loan loan, Book book, LocalDate date,
                        ReturnCondition condition, BigDecimal minorDamage) {
                return ChargeCalculator.returnCharges(loan, book, date, condition, minorDamage,
                                settings.getMoney("fee.damagedProcessing"));
        }

        /**
         * Returns an active or lost loan and commits its loan, book, and ledger changes
         * together.
         */
        public Loan receive(User actor, String loanId, ReturnCondition condition, BigDecimal minorDamage)
                        throws IOException {
                return receiveMany(actor, List.of(new ReturnRequest(loanId, condition, minorDamage))).getFirst();
        }

        /** Returns multiple selected loans with one loan/book/ledger transaction. */
        public List<Loan> receiveMany(User actor, List<ReturnRequest> requests) throws IOException {
                require(actor);
                if (requests == null || requests.isEmpty()) {
                        throw new IllegalArgumentException("Select at least one loan to return.");
                }
                LocalDate today = LocalDate.now();
                List<Loan> oldLoans = loans.findAll();
                List<Book> oldBooks = books.findAll();
                List<Charge> oldCharges = charges.allRecords();
                List<Loan> nextLoans = new ArrayList<>(oldLoans);
                List<Charge> nextCharges = new ArrayList<>(oldCharges);
                java.util.Map<String, Book> booksById = new java.util.HashMap<>();
                oldBooks.forEach(book -> booksById.put(book.bookId(), book));
                java.util.Set<String> seenLoans = new java.util.HashSet<>();
                List<Loan> returned = new ArrayList<>();
                List<Book> freedCopies = new ArrayList<>();

                for (ReturnRequest request : requests) {
                        if (!seenLoans.add(request.loanId())) {
                                throw new IllegalArgumentException("A loan can only be returned once per action.");
                        }
                        int loanIndex = index(nextLoans, request.loanId());
                        Loan loan = nextLoans.get(loanIndex);
                        if (loan.status() != LoanStatus.ACTIVE && loan.status() != LoanStatus.LOST) {
                                throw new IllegalArgumentException("This loan is already closed.");
                        }
                        Book book = booksById.get(loan.bookId());
                        if (book == null)
                                throw new IllegalArgumentException("The loan's book no longer exists.");
                        boolean foundLost = loan.status() == LoanStatus.LOST;
                        LocalDate lostOn = foundLost ? loan.returnDate() : null;
                        boolean refundWindow = foundLost && lostOn != null
                                        && !today.isAfter(lostOn.plusDays(settings.getInt("refund.windowDays")));
                        List<ChargeCalculator.Line> allLines = preview(loan, book, today, request.condition(),
                                        request.minorDamage());
                        List<ChargeCalculator.Line> returnLines = foundLost
                                        ? allLines.stream().filter(line -> line.type() == ChargeType.DAMAGED_BOOK
                                                        || line.type() == ChargeType.PROCESSING_FEE).toList()
                                        : allLines;
                        Loan updated = new Loan(loan.loanId(), loan.bookId(), loan.bookTitleSnapshot(), loan.memberId(),
                                        loan.issueDate(), loan.dueDate(), today, LoanStatus.RETURNED,
                                        foundLost ? loan.baseCharge() : sum(returnLines, ChargeType.BASE),
                                        foundLost ? loan.rentCharge() : sum(returnLines, ChargeType.RENT),
                                        foundLost ? loan.overdueFine() : sum(returnLines, ChargeType.OVERDUE_FINE),
                                        request.condition().name(), loan.issuedBy(), actor.username(),
                                        loan.pricingSnapshot());
                        nextLoans.set(loanIndex, updated);

                        int total = book.totalCopies();
                        int available = book.availableCopies();
                        if (foundLost && request.condition() != ReturnCondition.SEVERE_DAMAGE) {
                                total++;
                                available++;
                        } else if (!foundLost && request.condition() == ReturnCondition.SEVERE_DAMAGE) {
                                total = Math.max(0, total - 1);
                        } else if (!foundLost) {
                                available++;
                        }
                        Book updatedBook = withInventory(book, total, available);
                        booksById.put(book.bookId(), updatedBook);
                        if (request.condition() != ReturnCondition.SEVERE_DAMAGE)
                                freedCopies.add(book);

                        List<ChargeCalculator.Line> ledgerLines = returnLines.stream()
                                        .filter(line -> line.type() != ChargeType.BASE).toList();
                        nextCharges.addAll(charges.createAll(loan.memberId(), loan.loanId(), ledgerLines));
                        if (refundWindow)
                                recoverLostCharge(loan, nextCharges);
                        returned.add(updated);
                }
                List<Book> nextBooks = oldBooks.stream().map(book -> booksById.get(book.bookId())).toList();
                try {
                        loans.saveAll(nextLoans);
                        books.saveAll(nextBooks);
                        charges.replaceAll(nextCharges);
                } catch (IOException | RuntimeException exception) {
                        rollback(oldLoans, oldBooks, oldCharges, exception);
                        throw exception;
                }
                if (reservations != null) {
                        for (Book book : freedCopies)
                                reservations.onCopyFreed(book.bookId());
                }
                returned.forEach(loan -> events.onReturned(loan,
                                !ReturnCondition.GOOD.name().equals(loan.returnCondition()), loan.overdueFine()));
                return List.copyOf(returned);
        }

        private void recoverLostCharge(Loan loan, List<Charge> nextCharges) {
                int index = -1;
                for (int i = 0; i < nextCharges.size(); i++) {
                        Charge charge = nextCharges.get(i);
                        if (charge.memberId().equals(loan.memberId()) && charge.loanId().equals(loan.loanId())
                                        && charge.type() == ChargeType.LOST_BOOK) {
                                index = i;
                                break;
                        }
                }
                if (index < 0)
                        return;
                Charge lostCharge = nextCharges.get(index);
                BigDecimal unpaid = lostCharge.balance().max(BigDecimal.ZERO).setScale(2);
                BigDecimal paid = lostCharge.paidAmount().max(BigDecimal.ZERO).setScale(2);
                if (unpaid.signum() > 0) {
                        BigDecimal waived = lostCharge.waivedAmount().add(unpaid);
                        lostCharge = new Charge(lostCharge.chargeId(), lostCharge.memberId(), lostCharge.loanId(),
                                        lostCharge.type(), lostCharge.description() + " | Book recovered",
                                        lostCharge.amount(),
                                        lostCharge.waivedAmount(), lostCharge.paidAmount(), lostCharge.status(),
                                        lostCharge.createdAt())
                                        .withAmounts(waived, lostCharge.paidAmount());
                        nextCharges.set(index, lostCharge);
                }
                if (paid.signum() > 0) {
                        nextCharges.add(charges.create(loan.memberId(), loan.loanId(), ChargeType.REFUND,
                                        "Refund due to member", paid.negate()));
                }
        }

        private void rollback(List<Loan> oldLoans, List<Book> oldBooks, List<Charge> oldCharges, Throwable cause) {
                try {
                        loans.saveAll(oldLoans);
                } catch (IOException exception) {
                        cause.addSuppressed(exception);
                }
                try {
                        books.saveAll(oldBooks);
                } catch (IOException exception) {
                        cause.addSuppressed(exception);
                }
                try {
                        charges.replaceAll(oldCharges);
                } catch (IOException exception) {
                        cause.addSuppressed(exception);
                }
        }

        private void require(User actor) {
                if (actor == null || !actor.active() || !permissions.canCirculate(actor)) {
                        throw new SecurityException("Only staff or an administrator can receive returns.");
                }
        }

        private static BigDecimal sum(List<ChargeCalculator.Line> lines, ChargeType type) {
                return lines.stream().filter(line -> line.type() == type).map(ChargeCalculator.Line::amount)
                                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        }

        private static int index(List<Loan> records, String id) {
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
                                book.publicationYear(), book.edition(), book.category(), book.language(),
                                book.shelfLocation(),
                                book.price(), total, available, book.dateAdded());
        }

        public record ReturnRequest(String loanId, ReturnCondition condition, BigDecimal minorDamage) {
        }
}
