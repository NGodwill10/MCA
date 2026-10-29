package data;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Loan;
import model.LoanDirection;
import model.LoanStatus;
import model.PaymentStatus;
import model.ReturnCondition;

public final class CsvLoanRepository implements LoanRepository {
    private static final List<String> HEADER = List.of("loanId", "direction", "bookId", "bookTitleSnapshot",
            "contactId", "issueDate", "dueDate", "returnDate", "status", "baseCharge", "rentCharge",
            "overduePenalty", "damageCharge", "returnCondition", "paymentStatus", "pricingSnapshot",
            "schemaVersion");
    private final Path file;
    private List<Loan> loans;

    public CsvLoanRepository(Path file) throws IOException {
        this.file = file;
        loans = loadLoans();
        if (!CsvUtil.hasHeaderColumn(file, "schemaVersion")) {
            writeLoans(loans);
        }
    }

    @Override
    public List<Loan> findAll() {
        return List.copyOf(loans);
    }

    @Override
    public void saveAll(List<Loan> updatedLoans) throws IOException {
        List<Loan> copy = List.copyOf(updatedLoans);
        writeLoans(copy);
        loans = copy;
    }

    private List<Loan> loadLoans() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        if (records.isEmpty()) {
            return List.of();
        }
        List<String> header = records.getFirst();
        List<Loan> loaded = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> row = records.get(rowIndex);
            try {
                loaded.add(new Loan(value(row, header, "loanId"), enumValue(LoanDirection.class,
                        value(row, header, "direction")), value(row, header, "bookId"),
                        value(row, header, "bookTitleSnapshot"), value(row, header, "contactId"),
                        LocalDate.parse(value(row, header, "issueDate")),
                        LocalDate.parse(value(row, header, "dueDate")), optionalDate(value(row, header, "returnDate")),
                        enumValue(LoanStatus.class, value(row, header, "status")),
                        decimal(value(row, header, "baseCharge")), optionalMoney(value(row, header, "rentCharge")),
                        optionalMoney(value(row, header, "overduePenalty")),
                        optionalMoney(value(row, header, "damageCharge")),
                        optionalEnum(ReturnCondition.class, value(row, header, "returnCondition")),
                        enumValue(PaymentStatus.class, value(row, header, "paymentStatus")),
                        value(row, header, "pricingSnapshot")));
            } catch (RuntimeException exception) {
                System.getLogger(CsvLoanRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed loan row " + (rowIndex + 1) + " in " + file, exception);
            }
        }
        return List.copyOf(loaded);
    }

    private void writeLoans(List<Loan> records) throws IOException {
        List<List<String>> rows = records.stream().map(loan -> List.of(loan.loanId(), loan.direction().name(),
                loan.bookId(), loan.bookTitleSnapshot(), loan.contactId(), loan.issueDate().toString(),
                loan.dueDate().toString(), date(loan.returnDate()), display(loan.status()),
                loan.baseCharge().toPlainString(), money(loan.rentCharge()), money(loan.overduePenalty()),
                money(loan.damageCharge()), display(loan.returnCondition()), display(loan.paymentStatus()),
                loan.pricingSnapshot(), CsvUtil.SCHEMA_VERSION)).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }

    private static String value(List<String> row, List<String> header, String name) {
        int index = header.indexOf(name);
        return index < 0 || index >= row.size() ? "" : row.get(index);
    }

    private static String date(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String money(BigDecimal amount) {
        return amount == null ? "" : amount.toPlainString();
    }

    private static String display(LoanStatus status) {
        return status == LoanStatus.ACTIVE ? "Active" : "Returned";
    }

    private static String display(PaymentStatus status) {
        return switch (status) {
            case NONE -> "None";
            case OWED -> "Owed";
            case PAID -> "Paid";
        };
    }

    private static String display(ReturnCondition condition) {
        if (condition == null) {
            return "";
        }
        return switch (condition) {
            case GOOD -> "Good";
            case MINOR_DAMAGE -> "MinorDamage";
            case SEVERE_DAMAGE -> "SevereDamage";
        };
    }

    private static LocalDate optionalDate(String value) {
        return value.isBlank() ? null : LocalDate.parse(value);
    }

    private static BigDecimal optionalMoney(String value) {
        return value.isBlank() ? null : decimal(value);
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    private static <E extends Enum<E>> E optionalEnum(Class<E> type, String value) {
        return value.isBlank() ? null : enumValue(type, value);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (type == ReturnCondition.class) {
            normalized = switch (normalized.replace(" ", "").replace("_", "")) {
                case "MINORDAMAGE" -> "MINOR_DAMAGE";
                case "SEVEREDAMAGE" -> "SEVERE_DAMAGE";
                default -> normalized;
            };
        }
        return Enum.valueOf(type, normalized);
    }
}