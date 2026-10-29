import data.CsvUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class CsvRecoveryTest {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("libgrampocket-csv-recovery-");
        try {
            restoresReadableBackup(directory);
            quarantinesBothUnreadableCopies(directory);
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
        System.out.println("CsvRecoveryTest passed");
    }

    private static void restoresReadableBackup(Path directory) throws Exception {
        Path file = directory.resolve("books.txt");
        Files.writeString(file, "broken,\"unterminated\n");
        Files.writeString(directory.resolve("books.txt.bak"), "id,title\n1,Recovered\n");

        List<List<String>> rows = CsvUtil.read(file);

        check(rows.size() == 2 && rows.get(1).get(1).equals("Recovered"));
        check(Files.readString(file).contains("Recovered"));
        check(countCorruptFiles(directory) == 1);
    }

    private static void quarantinesBothUnreadableCopies(Path directory) throws Exception {
        Path file = directory.resolve("loans.txt");
        Files.writeString(file, "broken,\"unterminated\n");
        Files.writeString(directory.resolve("loans.txt.bak"), "broken,\"unterminated\n");

        check(CsvUtil.read(file).isEmpty());
        check(!Files.exists(file));
        check(!Files.exists(directory.resolve("loans.txt.bak")));
        check(countCorruptFiles(directory) == 3);
    }

    private static long countCorruptFiles(Path directory) throws Exception {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().contains(".corrupt-")).count();
        }
    }

    private static void check(boolean condition) {
        if (!condition) {
            throw new AssertionError("CSV recovery assertion failed");
        }
    }
}