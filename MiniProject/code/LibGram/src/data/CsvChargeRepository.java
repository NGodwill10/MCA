package data;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import model.Charge;
import model.ChargeStatus;
import model.ChargeType;

public final class CsvChargeRepository implements ChargeRepository {
    private static final List<String> HEADER = List.of("chargeId", "memberId", "loanId", "type", "description",
            "amount", "waivedAmount", "paidAmount", "status", "createdAt");
    private final Path file;
    private List<Charge> charges;
    public CsvChargeRepository(Path file) throws IOException { this.file = file; charges = load(); if (!Files.exists(file)) write(charges); }
    public List<Charge> findAll() { return List.copyOf(charges); }
    public void saveAll(List<Charge> records) throws IOException { List<Charge> copy = List.copyOf(records); write(copy); charges = copy; }
    private List<Charge> load() throws IOException {
        List<List<String>> rows = CsvUtil.read(file); if (rows.isEmpty()) return List.of();
        List<String> header = rows.getFirst(); List<Charge> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) try {
            List<String> row = rows.get(i);
            result.add(new Charge(value(row, header, "chargeId"), value(row, header, "memberId"), value(row, header, "loanId"),
                    ChargeType.valueOf(value(row, header, "type").toUpperCase(Locale.ROOT)), value(row, header, "description"),
                    money(value(row, header, "amount")), money(value(row, header, "waivedAmount")), money(value(row, header, "paidAmount")),
                    ChargeStatus.valueOf(value(row, header, "status").toUpperCase(Locale.ROOT)), LocalDateTime.parse(value(row, header, "createdAt"))));
        } catch (RuntimeException ex) { System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING, "Skipping malformed charge row", ex); }
        return List.copyOf(result);
    }
    private void write(List<Charge> records) throws IOException {
        List<List<String>> rows = records.stream().map(c -> List.of(c.chargeId(), c.memberId(), c.loanId(), c.type().name(), c.description(),
                c.amount().setScale(2).toPlainString(), c.waivedAmount().setScale(2).toPlainString(), c.paidAmount().setScale(2).toPlainString(),
                c.status().name(), c.createdAt().toString())).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }
    private static String value(List<String> row, List<String> header, String key) { int i = header.indexOf(key); return i >= 0 && i < row.size() ? row.get(i) : ""; }
    private static BigDecimal money(String value) { return value.isBlank() ? BigDecimal.ZERO.setScale(2) : new BigDecimal(value).setScale(2); }
}
