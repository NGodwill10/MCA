package data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvSettingsRepository implements SettingsRepository {
    private static final List<String> HEADER = List.of("key", "value");
    private final Path file;
    private Map<String, String> values;

    public CsvSettingsRepository(Path file) throws IOException {
        this.file = file;
        values = readValues();
        boolean needsWrite = !Files.exists(file) || Files.size(file) == 0;
        needsWrite |= values.putIfAbsent("schemaVersion", CsvUtil.SCHEMA_VERSION) == null;
        if (needsWrite) {
            save(values);
        }
    }

    @Override
    public Map<String, String> load() {
        return Map.copyOf(values);
    }

    @Override
    public void save(Map<String, String> updatedValues) throws IOException {
        Map<String, String> copy = new LinkedHashMap<>(updatedValues);
        copy.putIfAbsent("schemaVersion", CsvUtil.SCHEMA_VERSION);
        List<List<String>> rows = copy.entrySet().stream()
                .map(entry -> List.of(entry.getKey(), entry.getValue())).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
        values = copy;
    }

    private Map<String, String> readValues() throws IOException {
        List<List<String>> records = CsvUtil.read(file);
        Map<String, String> loaded = new LinkedHashMap<>();
        if (records.isEmpty()) {
            return loaded;
        }
        List<String> header = records.getFirst();
        int keyIndex = header.indexOf("key");
        int valueIndex = header.indexOf("value");
        if (keyIndex < 0 || valueIndex < 0) {
            return loaded;
        }
        for (int rowIndex = 1; rowIndex < records.size(); rowIndex++) {
            List<String> row = records.get(rowIndex);
            if (row.size() != HEADER.size() || row.get(keyIndex).isBlank()) {
                System.getLogger(CsvSettingsRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed settings row " + (rowIndex + 1) + " in " + file);
                continue;
            }
            loaded.put(row.get(keyIndex), row.get(valueIndex));
        }
        return loaded;
    }
}