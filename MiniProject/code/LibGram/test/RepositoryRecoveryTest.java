import data.CsvUtil;
import java.nio.file.Files;
import java.nio.file.Path;

public final class RepositoryRecoveryTest {
    private RepositoryRecoveryTest() { }

    public static void main(String[] args) throws Exception {
        Path folder = Files.createTempDirectory("libgram-recovery-");
        try {
            Path recovered = folder.resolve("recovered.txt");
            Files.writeString(recovered, "a,b\n\"broken\n");
            Files.writeString(recovered.resolveSibling("recovered.txt.bak"), "a,b\nold,value\n");
            TestSupport.equals("old", CsvUtil.read(recovered).get(1).get(0), "Unreadable CSV recovers from backup");
            TestSupport.check(CsvUtil.consumeRecoveryNotices().stream().anyMatch(text -> text.contains("recovered.txt")),
                    "Recovery notice names the file");

            Path corrupt = folder.resolve("corrupt.txt");
            Files.writeString(corrupt, "a,b\n\"broken\n");
            Files.writeString(corrupt.resolveSibling("corrupt.txt.bak"), "a,b\n\"also broken\n");
            TestSupport.check(CsvUtil.read(corrupt).isEmpty(), "Double corruption starts with empty records");
            try (var paths = Files.list(folder)) {
                TestSupport.check(paths.anyMatch(path -> path.getFileName().toString().startsWith("corrupt.txt.corrupt-")),
                        "Corrupt file is preserved with a timestamp suffix");
            }
            TestSupport.check(CsvUtil.consumeRecoveryNotices().stream().anyMatch(text -> text.contains("corrupt.txt")),
                    "Empty-dataset recovery notice names the file");
            System.out.println("Repository recovery tests passed.");
        } finally {
            TestSupport.deleteTree(folder);
        }
    }
}
