import data.CsvSettingsRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class CsvSettingsRepositoryTest {
    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("pocket-settings-repository-test");
        Path file = root.resolve("settings.txt");
        CsvSettingsRepository repository = new CsvSettingsRepository(file);
        check(repository.load().get("schemaVersion").equals("1"));
        repository.save(Map.of("custom.key", "value, with comma", "another.key", "quoted \"value\""));
        String previous = Files.readString(file);
        repository.save(Map.of("custom.key", "updated"));
        check(Files.readString(file.resolveSibling("settings.txt.bak")).equals(previous));
        Map<String, String> loaded = new CsvSettingsRepository(file).load();
        check(loaded.get("custom.key").equals("updated"));
        check(loaded.get("schemaVersion").equals("1"));

        Path corruptFile = root.resolve("corrupt-settings.txt");
        data.CsvUtil.writeAtomically(corruptFile, List.of("key", "value"),
                List.of(List.of("valid.key", "kept"), List.of("incomplete"), List.of("also.valid", "yes")));
        CsvSettingsRepository defensive = new CsvSettingsRepository(corruptFile);
        check(defensive.load().get("valid.key").equals("kept"));
        check(defensive.load().get("also.valid").equals("yes"));
        check(defensive.load().get("schemaVersion").equals("1"));
        System.out.println("CsvSettingsRepositoryTest passed");
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("CSV settings assertion failed");
        }
    }
}