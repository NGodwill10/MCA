package data;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Payment;
import model.PaymentMethod;

public final class CsvPaymentRepository implements PaymentRepository {
    private static final List<String> HEADER = List.of("paymentId", "memberId", "chargeId", "amount", "method", "receivedBy", "paidAt");
    private final Path file; private List<Payment> payments;
    public CsvPaymentRepository(Path file) throws IOException { this.file = file; payments = load(); if (!Files.exists(file)) write(payments); }
    public List<Payment> findAll() { return List.copyOf(payments); }
    public void saveAll(List<Payment> records) throws IOException { List<Payment> copy = List.copyOf(records); write(copy); payments = copy; }
    private List<Payment> load() throws IOException {
        List<List<String>> rows = CsvUtil.read(file); if (rows.isEmpty()) return List.of(); List<String> header = rows.getFirst(); List<Payment> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) { try { List<String> row = rows.get(i); result.add(new Payment(value(row, header, "paymentId"), value(row, header, "memberId"), value(row, header, "chargeId"),
                new BigDecimal(value(row, header, "amount")).setScale(2), PaymentMethod.valueOf(value(row, header, "method").toUpperCase(Locale.ROOT)), value(row, header, "receivedBy"), LocalDateTime.parse(value(row, header, "paidAt"))));
            } catch (RuntimeException ex) { System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Skipping malformed payment row", ex); } }
        return List.copyOf(result);
    }
    private void write(List<Payment> records) throws IOException { CsvUtil.writeAtomically(file, HEADER, records.stream().map(p -> List.of(p.paymentId(), p.memberId(), p.chargeId(), p.amount().setScale(2).toPlainString(), p.method().name(), p.receivedBy(), p.paidAt().toString())).toList()); }
    private static String value(List<String> row, List<String> header, String key) { int i = header.indexOf(key); return i >= 0 && i < row.size() ? row.get(i) : ""; }
}
