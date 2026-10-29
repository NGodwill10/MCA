import data.CsvUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class CsvRoundTripTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("libgrampocket-csv-test-");
        try {
            Path file = directory.resolve("values.txt");
            List<String> header = List.of("value");
            List<List<String>> expected = List.of(List.of("Smith, John"), List.of("A \"quoted\" value"),
                    List.of("first line\nsecond line"), List.of("  spaces kept  "),
                    List.of("Devanagari: पुस्तक, café, naïve"));
            CsvUtil.writeAtomically(file, header, expected);
            check(CsvUtil.read(file).equals(join(header, expected)));
            System.out.println("CsvRoundTripTest passed");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static List<List<String>> join(List<String> header, List<List<String>> rows) {
        java.util.ArrayList<List<String>> records = new java.util.ArrayList<>();
        records.add(header);
        records.addAll(rows);
        return List.copyOf(records);
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("CSV quoting assertion failed");
        }
    }
}