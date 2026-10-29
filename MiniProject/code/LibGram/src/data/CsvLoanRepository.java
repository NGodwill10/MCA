package data;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Loan;
import model.LoanStatus;

public final class CsvLoanRepository implements LoanRepository {
    private static final List<String> HEADER = List.of("loanId", "bookId", "bookTitleSnapshot", "memberId",
            "issueDate", "dueDate", "returnDate", "status", "baseCharge", "rentCharge", "overdueFine",
            "returnCondition", "issuedBy", "receivedBy", "pricingSnapshot");
    private final Path file;
    private List<Loan> loans;

    public CsvLoanRepository(Path file) throws IOException {
        this.file = file;
        loans = load();
        if (!Files.exists(file) || Files.size(file) == 0) {
            write(loans);
        }
    }

    @Override
    public List<Loan> findAll() {
        return List.copyOf(loans);
    }

    @Override
    public void saveAll(List<Loan> records) throws IOException {
        List<Loan> copy = List.copyOf(records);
        write(copy);
        loans = copy;
    }

    private List<Loan> load() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        if (records.isEmpty()) return List.of();
        List<String> header = records.getFirst();
        List<Loan> loaded = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            try {
                List<String> row = records.get(rowIndex);
                loaded.add(new Loan(value(row, header, "loanId"), value(row, header, "bookId"),
                        value(row, header, "bookTitleSnapshot"), value(row, header, "memberId"),
                        LocalDate.parse(value(row, header, "issueDate")),
                        LocalDate.parse(value(row, header, "dueDate")), date(value(row, header, "returnDate")),
                        LoanStatus.valueOf(value(row, header, "status").toUpperCase(Locale.ROOT)),
                        money(value(row, header, "baseCharge")), money(value(row, header, "rentCharge")),
                        money(value(row, header, "overdueFine")), value(row, header, "returnCondition"),
                        value(row, header, "issuedBy"), value(row, header, "receivedBy"),
                        value(row, header, "pricingSnapshot")));
            } catch (RuntimeException exception) {
                System.getLogger(CsvLoanRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed loan row " + (rowIndex + 1) + " in " + file, exception);
            }
        }
        return List.copyOf(loaded);
    }

    private void write(List<Loan> records) throws IOException {
        List<List<String>> rows = records.stream().map(loan -> List.of(loan.loanId(), loan.bookId(),
                loan.bookTitleSnapshot(), loan.memberId(), loan.issueDate().toString(), loan.dueDate().toString(),
                text(loan.returnDate()), loan.status().name(), moneyText(loan.baseCharge()),
                moneyText(loan.rentCharge()), moneyText(loan.overdueFine()), loan.returnCondition(),
                loan.issuedBy(), loan.receivedBy(), loan.pricingSnapshot())).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }

    private static BigDecimal money(String value) {
        return value.isBlank() ? BigDecimal.ZERO.setScale(2) : new BigDecimal(value).setScale(2);
    }

    private static String moneyText(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2).toPlainString();
    }

    private static String text(LocalDate date) { return date == null ? "" : date.toString(); }

    private static LocalDate date(String value) { return value.isBlank() ? null : LocalDate.parse(value); }

    private static String value(List<String> row, List<String> header, String name) {
        int index = header.indexOf(name);
        return index < 0 || index >= row.size() ? "" : row.get(index);
    }
}
