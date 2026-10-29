package data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import services.SettingsDefaults;

/** CSV-backed settings repository. Unknown keys are retained verbatim. */
public final class CsvSettingsRepository implements SettingsRepository {
    private static final List<String> HEADER = List.of("key", "value");
    private final Path file;

    public CsvSettingsRepository(Path file) throws IOException {
        this.file = file;
        if (!Files.exists(file) || Files.size(file) == 0) {
            save(SettingsDefaults.values());
        }
    }

    @Override
    public Map<String, String> load() throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        List<List<String>> rows = CsvUtil.read(file);
        for (int row = 1; row < rows.size(); row++) {
            List<String> record = rows.get(row);
            if (record.size() < 2 || record.get(0).isBlank()) {
                System.getLogger(CsvSettingsRepository.class.getName()).log(System.Logger.Level.WARNING,
                        "Skipping malformed settings row " + (row + 1) + " in " + file);
                continue;
            }
            values.put(record.get(0), record.get(1));
        }
        return Map.copyOf(values);
    }

    @Override
    public void save(Map<String, String> values) throws IOException {
        List<List<String>> rows = values.entrySet().stream()
                .map(entry -> List.of(entry.getKey(), entry.getValue())).toList();
        CsvUtil.writeAtomically(file, HEADER, rows);
    }
}
